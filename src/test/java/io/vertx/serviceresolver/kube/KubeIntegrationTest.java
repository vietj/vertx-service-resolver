package io.vertx.serviceresolver.kube;

import com.dajudge.kindcontainer.ApiServerContainer;
import com.dajudge.kindcontainer.K3sContainer;
import com.dajudge.kindcontainer.KindContainer;
import com.dajudge.kindcontainer.client.TinyK8sClient;
import com.dajudge.kindcontainer.client.model.apps.v1.Deployment;
import com.dajudge.kindcontainer.client.model.v1.Node;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.http.TlsVersion;
import io.fabric8.kubernetes.client.internal.SSLUtils;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.TrustOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.serviceresolver.ServiceResolverTestBase;
import io.vertx.serviceresolver.kube.impl.KubeResolver;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.shaded.com.fasterxml.jackson.core.type.TypeReference;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.dajudge.kindcontainer.DeploymentAvailableWaitStrategy.deploymentIsAvailable;
import static io.fabric8.kubernetes.client.Config.fromKubeconfig;

@RunWith(VertxUnitRunner.class)
public class KubeIntegrationTest {

  @Rule
//  public K3sContainer<?> K8S = new K3sContainer<>();
  public KindContainer<?> K8S = new KindContainer<>();

  private KubernetesClient client;
  private TinyK8sClient tiny;
  private Config cfg;
  private Vertx vertx;

  @Before
  public void setUp() throws Exception {
    cfg = fromKubeconfig(K8S.getKubeconfig());
    client = ((NamespacedKubernetesClient)new KubernetesClientBuilder()
      .withConfig(cfg).build()).inNamespace("default");
    tiny = TinyK8sClient.fromKubeconfig(K8S.getKubeconfig());
    vertx = Vertx.vertx();
  }

  @Test
  public void testDeploy(TestContext should) throws Exception {

    System.out.println("Deploying");
    K8S.withKubectl(kubeCtl -> {
      kubeCtl.apply.namespace("default").fileFromClasspath("deployment.yml").run();
    });

    System.out.println("Deployed");

    Pod pod = client.pods().inNamespace("default").waitUntilCondition(p -> {
      if (p == null || !"nginx".equals(p.getMetadata().getLabels().get("app"))) {
        System.out.println("No pod found");
        return false;
      }
      String phase = p.getStatus().getPhase();
      if (!phase.equals("Running")) {
        System.out.println("Found pod but in phase " + phase);
        return false;
      }
      return true;
    }, 300, TimeUnit.SECONDS);

    //
    System.out.println("Pod is ready " + pod);

    LocalPortForward forward = client.pods().withName(pod.getMetadata().getName()).portForward(80, 8080);

    Async async = should.async();
    HttpClient client = vertx.createHttpClient();
    client
      .request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> req
        .send()
        .compose(HttpClientResponse::body))
      .onComplete(should.asyncAssertSuccess(response -> {
        System.out.println("Response from pod " + response);
        async.complete();
      }));
    async.awaitSuccess();
  }
}
