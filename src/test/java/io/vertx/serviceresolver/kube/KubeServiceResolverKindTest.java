package io.vertx.serviceresolver.kube;

import com.dajudge.kindcontainer.ApiServerContainer;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.http.TlsVersion;
import io.fabric8.kubernetes.client.internal.SSLUtils;
import io.netty.util.NetUtil;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.net.*;
import io.vertx.serviceresolver.kube.impl.KubeResolver;
import org.junit.Rule;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fabric8.kubernetes.client.Config.fromKubeconfig;

public class KubeServiceResolverKindTest extends KubeServiceResolverTestBase {

  public static HttpClientOptions clientOptions(Config cfg) throws Exception {
    HttpClientOptions options = new HttpClientOptions();
    if (cfg.getTlsVersions() != null && cfg.getTlsVersions().length > 0) {
      Stream.of(cfg.getTlsVersions()).map(TlsVersion::javaName).forEach(options::addEnabledSecureTransportProtocol);
    }

    if (cfg.isHttp2Disable()) {
      options.setProtocolVersion(HttpVersion.HTTP_1_1);
    }

    TrustManager[] trustManagers = SSLUtils.trustManagers(cfg);
    KeyManager[] keyManagers = SSLUtils.keyManagers(cfg);
    options
      .setSsl(true)
      .setKeyCertOptions(KeyCertOptions.wrap((X509KeyManager) keyManagers[0]))
      .setTrustOptions(TrustOptions.wrap(trustManagers[0]));

    return options;
  }

  @Rule
//  public static K3sContainer<?> K8S = new K3sContainer<>();
  public ApiServerContainer<?> K8S = new ApiServerContainer<>();

  public void setUp() throws Exception {
    super.setUp();

    kubernetesMocking = new KubernetesMocking(K8S);

    Config cfg = fromKubeconfig(K8S.getKubeconfig());
    URL url = new URL(cfg.getMasterUrl());
    HttpClientOptions options = clientOptions(cfg);

    KubeResolver resolver = new KubeResolver(vertx, kubernetesMocking.defaultNamespace(), url.getHost(), url.getPort(), null, options);
    client = (HttpClientInternal) vertx.createHttpClient();
    client.addressResolver(resolver);
  }

  private String determineHostAddress() {
    for (NetworkInterface ni : NetUtil.NETWORK_INTERFACES) {
      Enumeration<InetAddress> addresses = ni.getInetAddresses();
      while (addresses.hasMoreElements()) {
        InetAddress address = addresses.nextElement();
        if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
          return address.getHostAddress();
        }
      }
    }
    return null;
  }

  @Override
  protected List<SocketAddress> startPods(int numPods, Handler<HttpServerRequest> service) throws Exception {
    String host = determineHostAddress();
    List<SocketAddress> pods = startPods(numPods, host, service);
    return pods.stream().map(sa -> SocketAddress.inetSocketAddress(sa.port(), host)).collect(Collectors.toList());
  }
}
