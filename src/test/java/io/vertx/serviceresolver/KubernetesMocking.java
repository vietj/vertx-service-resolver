package io.vertx.serviceresolver;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.vertx.core.net.SocketAddress;
import junit.framework.AssertionFailedError;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class KubernetesMocking {

  private final KubernetesServer server;
  private KubernetesClient client;
  private int port;

  public KubernetesMocking(KubernetesServer server) {

    NamespacedKubernetesClient client = server.getClient();
    int port;
    try {
      port = new URL(client.getConfiguration().getMasterUrl()).getPort();
    } catch (MalformedURLException e) {
      AssertionFailedError afe = new AssertionFailedError();
      afe.initCause(e);
      throw afe;
    }

    this.server = server;
    this.client = client;
    this.port = port;
  }

  public int port() {
    return port;
  }

  public String defaultNamespace() {
    return client.getNamespace();
  }

  void registerKubernetesResources(String serviceName, String namespace, SocketAddress... ips) {
    buildAndRegisterKubernetesService(serviceName, namespace, true, ips);
    Arrays.stream(ips).forEach(ip -> buildAndRegisterBackendPod(serviceName, namespace, true, ip));
  }

  Endpoints buildAndRegisterKubernetesService(String applicationName, String namespace, boolean register,
                                                      SocketAddress... ipAdresses) {

    Map<String, String> serviceLabels = new HashMap<>();
    serviceLabels.put("app.kubernetes.io/name", applicationName);
    serviceLabels.put("app.kubernetes.io/version", "1.0");

    Map<Integer, List<EndpointAddress>> endpointAddressesMap = new LinkedHashMap<>();
    for (SocketAddress sa : ipAdresses) {
      List<EndpointAddress> endpointAddresses = endpointAddressesMap.compute(sa.port(), (integer, addresses) -> {
        if (addresses == null) {
          addresses = new ArrayList<>();
        }
        return addresses;
      });
      ObjectReference targetRef = new ObjectReference(null, null, "Pod",
        applicationName + "-" + ipAsSuffix(sa), namespace, null, UUID.randomUUID().toString());
      EndpointAddress endpointAddress = new EndpointAddressBuilder().withIp(sa.host()).withTargetRef(targetRef)
        .build();
      endpointAddresses.add(endpointAddress);
    }

    EndpointsBuilder endpointsBuilder = new EndpointsBuilder()
      .withNewMetadata().withName(applicationName).withLabels(serviceLabels).endMetadata();

    endpointAddressesMap.forEach((port, addresses) -> {
      endpointsBuilder.addToSubsets(new EndpointSubsetBuilder().withAddresses(addresses)
        .addToPorts(new EndpointPort[] { new EndpointPortBuilder().withPort(8080).withProtocol("TCP").build() })
        .build());
    });

    if (register) {
      if (namespace != null) {
        client.endpoints().inNamespace(namespace).resource(endpointsBuilder.build()).create();
      } else {
        client.endpoints().resource(endpointsBuilder.build()).create();
      }
    }
    return endpointsBuilder.build();
  }

  Pod buildAndRegisterBackendPod(String name, String namespace, boolean register, SocketAddress ip) {

    Map<String, String> serviceLabels = new HashMap<>();
    serviceLabels.put("app.kubernetes.io/name", name);
    serviceLabels.put("app.kubernetes.io/version", "1.0");

    Map<String, String> podLabels = new HashMap<>(serviceLabels);
    podLabels.put("ui", "ui-" + ipAsSuffix(ip));
    Pod backendPod = new PodBuilder().withNewMetadata().withName(name + "-" + ipAsSuffix(ip))
      .withLabels(podLabels)
      .withNamespace(namespace)
      .endMetadata()
      .build();
    if (register) {
      if (namespace != null) {
        client.pods().inNamespace(namespace).resource(backendPod).create();
      } else {
        client.pods().resource(backendPod).create();
      }
    }
    return backendPod;
  }

  String ipAsSuffix(SocketAddress ipAddress) {
    return ipAddress.host().replace(".", "") + "-" + ipAddress.port();
  }
}
