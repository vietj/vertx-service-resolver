package io.vertx.serviceresolver;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.serviceresolver.impl.ServiceResolver;
import junit.framework.AssertionFailedError;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.UndeclaredThrowableException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class ServiceResolverTest {

  @Rule
  public KubernetesServer server = new KubernetesServer(false, true, InetAddress.getLoopbackAddress(), 8443, Collections.emptyList());

  private Vertx vertx;
  private HttpClientInternal client;
  private KubernetesMocking kubernetesMocking;
  private List<HttpServer> pods;
  private HttpProxy proxy;

  @Before
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    kubernetesMocking = new KubernetesMocking(server);
    pods = new ArrayList<>();

    proxy = new HttpProxy(vertx);
    proxy.origin(SocketAddress.inetSocketAddress(kubernetesMocking.port(), "localhost"));
    proxy.port(1234);
    proxy.start();

    ServiceResolver resolver = new ServiceResolver(vertx, kubernetesMocking.defaultNamespace(), "localhost", 1234, null);
    client = (HttpClientInternal) vertx.createHttpClient();
    client.addressResolver(resolver);
  }

  @After
  public void tearDown() throws Exception {
    vertx.close()
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);
  }

  private void stopPods(Predicate<HttpServer> pred) throws Exception {
    Set<HttpServer> stopped = new HashSet<>();
    for (int i = 0;i < pods.size();i++) {
      HttpServer pod = pods.get(i);
      if (pred.test(pod)) {
        stopped.add(pod);
        pod.close().toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
      }
    }
    pods.removeAll(stopped);
  }

  private List<SocketAddress> startPods(int numPods, Handler<HttpServerRequest> service) throws Exception {
    int basePort = pods.isEmpty() ? 8080 : pods.get(pods.size() - 1).actualPort() + 1;
    List<HttpServer> started = new ArrayList<>();
    for (int i = 0;i < numPods;i++) {
      HttpServer pod = vertx
        .createHttpServer()
        .requestHandler(service);
      started.add(pod);
      pod.listen(basePort + i, "0.0.0.0")
        .toCompletionStage()
        .toCompletableFuture()
        .get(20, TimeUnit.SECONDS);
    }
    pods.addAll(started);
    return started
      .stream()
      .map(s -> SocketAddress.inetSocketAddress(s.actualPort(), "127.0.0.1"))
      .collect(Collectors.toList());
  }

  @Test
  public void testSimple(TestContext should) throws Exception {
    List<SocketAddress> pods = startPods(3, req -> {
      req.response().end("" + req.localAddress().port());
    });
    String serviceName = "svc";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(0));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(1));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(2));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods);
    should.assertEquals("8080", get().toString());
  }

  @Test
  public void testNoPods(TestContext should) throws Exception {
    try {
      get();
    } catch (Exception e) {
      should.assertEquals("No addresses for service svc", e.getMessage());
    }
  }

  @Test
  public void testSelect(TestContext should) throws Exception {
    List<SocketAddress> pods = startPods(3, req -> {
      req.response().end("" + req.localAddress().port());
    });
    String serviceName1 = "svc";
    String serviceName2 = "svc2";
    String serviceName3 = "svc3";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName1, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(0));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName2, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName1, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.subList(0, 1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName2, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.subList(1, 2));
    should.assertEquals("8080", get().toString());
    should.assertEquals("8080", get().toString());
    Thread.sleep(500); // Pause for some time to allow WebSocket to not concurrently run with updates
    kubernetesMocking.buildAndRegisterBackendPod(serviceName3, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(2));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName3, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.subList(2, 3));
    Thread.sleep(500); // Pause for some time to allow WebSocket to get changes
    should.assertEquals("8080", get().toString());
    should.assertEquals("8080", get().toString());
    should.assertEquals("8080", get().toString());
  }

  @Test
  public void testUpdate(TestContext should) throws Exception {
    Handler<HttpServerRequest> server = req -> {
      req.response().end("" + req.localAddress().port());
    };
    List<SocketAddress> pods = startPods(2, server);
    String serviceName = "svc";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(0));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.subList(0, 1));
    should.assertEquals("8080", get().toString());
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.UPDATE, pods);
    getUntil(body -> body.toString().equals("8081"));
  }

  @Test
  public void testDeletePod(TestContext should) throws Exception {
    Handler<HttpServerRequest> server = req -> {
      req.response().end("" + req.localAddress().port());
    };
    List<SocketAddress> pods = startPods(2, server);
    String serviceName = "svc";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(0));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods);
    should.assertEquals("8080", get().toString());
    should.assertEquals("8081", get().toString());
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.DELETE, pods.get(1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.UPDATE, pods.subList(0, 1));
    should.assertEquals("8080", get().toString());
    should.assertEquals("8080", get().toString());
  }

  @Test
  public void testDispose(TestContext should) throws Exception {
    Handler<HttpServerRequest> server = req -> {
      req.response().end("" + req.localAddress().port());
    };
    List<SocketAddress> pods = startPods(1, server);
    String serviceName = "svc";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(0));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods);
    should.assertEquals("8080", get().toString());
    assertWaitUntil(() -> proxy.webSockets().size() == 1);
    stopPods(pod -> true);
    assertWaitUntil(() -> proxy.webSockets().size() == 0);
  }

  @Test
  public void testReconnectWebSocket(TestContext should) throws Exception {
    Handler<HttpServerRequest> server = req -> {
      req.response().end("" + req.localAddress().port());
    };
    List<SocketAddress> pods = startPods(2, server);
    String serviceName = "svc";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(0));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.subList(0, 1));
    should.assertEquals("8080", get().toString());
    assertWaitUntil(() -> proxy.webSockets().size() == 1);
    WebSocketBase ws = proxy.webSockets().iterator().next();
    ws.close();
    assertWaitUntil(() -> proxy.webSockets().size() == 1 && !proxy.webSockets().contains(ws));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.CREATE, pods.get(1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), KubeOp.UPDATE, pods);
    should.assertEquals("8081", get().toString());
  }

  private void assertWaitUntil(Supplier<Boolean> cond) {
    long now = System.currentTimeMillis();
    while (!cond.get()) {
      if (System.currentTimeMillis() - now > 20_000) {
        throw new AssertionFailedError();
      }
    }
  }

  private Buffer get() throws Exception {
    Future<Buffer> fut = client
      .request(ServiceAddress.create("svc"), HttpMethod.GET, 80, "localhost", "/")
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    try {
      return fut.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw ((RuntimeException) cause);
      } else {
        throw new UndeclaredThrowableException(cause, cause.getMessage());
      }
    } catch (TimeoutException | InterruptedException e) {
      throw e;
    }
  }

  private void getUntil(Predicate<Buffer> test) throws Exception {
    int count = 10;
    for (int i = 0;i < count;i++) {
      Buffer body = get();
      if (test.test(body)) {
        return;
      }
    }
    throw new AssertionFailedError();
  }
}
