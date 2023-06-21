package io.vertx.serviceresolver.kube;

import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.vertx.core.Handler;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.serviceresolver.HttpProxy;
import io.vertx.serviceresolver.ServiceResolverTestBase;
import io.vertx.serviceresolver.impl.kube.KubeResolver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.net.InetAddress;
import java.util.*;

public class KubeServiceResolverTest extends ServiceResolverTestBase {

  @Rule
  public KubernetesServer server = new KubernetesServer(false, true, InetAddress.getLoopbackAddress(), 8443, Collections.emptyList());

  private KubernetesMocking kubernetesMocking;
  private HttpProxy proxy;

  public void setUp() throws Exception {
    super.setUp();
    kubernetesMocking = new KubernetesMocking(server);

    proxy = new HttpProxy(vertx);
    proxy.origin(SocketAddress.inetSocketAddress(kubernetesMocking.port(), "localhost"));
    proxy.port(1234);
    proxy.start();

    KubeResolver resolver = new KubeResolver(vertx, kubernetesMocking.defaultNamespace(), "localhost", 1234, null);
    client = (HttpClientInternal) vertx.createHttpClient();
    client.addressResolver(resolver);
  }

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
    assertWaitUntil(() -> get().toString().equals("8081"));
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
    assertWaitUntil(() -> get().toString().equals("8081"));
  }
}
