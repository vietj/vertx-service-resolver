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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RunWith(VertxUnitRunner.class)
public class ServiceResolverTest {

  @Rule
  public KubernetesServer server = new KubernetesServer(false, true);

  private Vertx vertx;
  private HttpClientInternal client;
  private KubernetesMocking kubernetesMocking;
  private List<HttpServer> pods;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    kubernetesMocking = new KubernetesMocking(server);
    pods = new ArrayList<>();

    ServiceResolver resolver = new ServiceResolver(vertx, kubernetesMocking.defaultNamespace(), "localhost", kubernetesMocking.port());
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
    stopPods(pod -> true);
    should.async();
  }

  private Buffer get() throws Exception {
    Future<Buffer> fut = client
      .request(ServiceAddress.create("svc"), HttpMethod.GET, 80, "localhost", "/")
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    return fut.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
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
