package io.vertx.serviceresolver;

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.serviceresolver.impl.ServiceResolver;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), false, pods.get(0));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), false, pods.get(1));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), false, pods.get(2));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), false, pods);
    Future<Buffer> fut = client
      .request(ServiceAddress.create("svc"), HttpMethod.GET, 80, "localhost", "/")
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    Buffer body = fut.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    should.assertEquals("8080", body.toString());
  }

  @Test
  public void testUpdate(TestContext should) throws Exception {
    Handler<HttpServerRequest> server = req -> {
      req.response().end("" + req.localAddress().port());
    };
    List<SocketAddress> pods = startPods(1, server);
    String serviceName = "svc";
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), false, pods.get(0));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), false, pods);
    Future<Buffer> fut = client
      .request(ServiceAddress.create("svc"), HttpMethod.GET, 80, "localhost", "/")
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    fut.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    Buffer body = fut.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    should.assertEquals("8080", body.toString());
    pods.addAll(startPods(1, server));
    kubernetesMocking.buildAndRegisterBackendPod(serviceName, kubernetesMocking.defaultNamespace(), false, pods.get(1));
    kubernetesMocking.buildAndRegisterKubernetesService(serviceName, kubernetesMocking.defaultNamespace(), true, pods);
    fut = client
      .request(ServiceAddress.create("svc"), HttpMethod.GET, 80, "localhost", "/")
      .compose(req -> req.send()
        .compose(HttpClientResponse::body));
    body = fut.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    should.assertEquals("8081", body.toString());
  }
}
