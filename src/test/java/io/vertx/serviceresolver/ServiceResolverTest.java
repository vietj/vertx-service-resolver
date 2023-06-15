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

import java.net.URL;
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
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    kubernetesMocking = new KubernetesMocking(server);

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

  private SocketAddress[] startPods(int numPods, Handler<HttpServerRequest> service) throws Exception {
    if (pods == null) {
      pods = new ArrayList<>();
      for (int i = 0;i < numPods;i++) {
        HttpServer pod = vertx
          .createHttpServer()
          .requestHandler(service);
        pods.add(pod);
        pod.listen(8080 + i, "0.0.0.0")
          .toCompletionStage()
          .toCompletableFuture()
          .get(20, TimeUnit.SECONDS);
      }
    }
    return pods
      .stream()
      .map(s -> SocketAddress.inetSocketAddress(s.actualPort(), "127.0.0.1"))
      .toArray(SocketAddress[]::new);
  }

  @Test
  public void testSimple(TestContext should) throws Exception {
    SocketAddress[] pods = startPods(3, req -> {
      req.response().end("" + req.localAddress().port());
    });
    String serviceName = "svc";
    kubernetesMocking.registerKubernetesResources(serviceName, kubernetesMocking.defaultNamespace(), pods);
    Async async = should.async();
    Future<Buffer> fut = client
      .request(ServiceAddress.create("svc"), HttpMethod.GET, 80, "localhost", "/")
      .compose(req -> req.send()
        .compose(resp -> resp.body()));
    fut.onComplete(should.asyncAssertSuccess(res -> {
      should.assertEquals("8080", res.toString());
      async.complete();
    }));
  }

}
