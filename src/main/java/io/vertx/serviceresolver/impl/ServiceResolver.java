package io.vertx.serviceresolver.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;
import io.vertx.serviceresolver.ServiceAddress;

import java.util.ArrayList;
import java.util.List;

public class ServiceResolver implements AddressResolver<ServiceState, ServiceAddress, Void> {

  private Vertx vertx;
  private String host;
  private int port;
  private HttpClient client;
  private String namespace;

  public ServiceResolver(Vertx vertx, String namespace, String host, int port) {
    this.vertx = vertx;
    this.namespace = namespace;
    this.host = host;
    this.port = port;
    this.client = vertx.createHttpClient();
  }

  @Override
  public ServiceAddress tryCast(Address address) {
    return address instanceof ServiceAddress ? (ServiceAddress) address : null;
  }

  @Override
  public Future<ServiceState> resolve(ServiceAddress serviceName) {
    return client
      .request(HttpMethod.GET, port, host, "/api/v1/namespaces/" + namespace + "/endpoints")
      .compose(req -> req.send().compose(resp -> {
        if (resp.statusCode() == 200) {
          return resp
            .body()
            .map(Buffer::toJsonObject);
        } else {
          return Future.failedFuture("Invalid status code " + resp.statusCode());
        }
      })).map(response -> {
        JsonArray items = response.getJsonArray("items");
        List<SocketAddress> podAddresses = new ArrayList<>();
        for (int i = 0;i < items.size();i++) {
          JsonObject item = items.getJsonObject(i);
          JsonObject metadata = item.getJsonObject("metadata");
          String name = metadata.getString("name");
          if (name.equals(serviceName.name())) {
            JsonArray subsets = item.getJsonArray("subsets");
            for (int j = 0;j < subsets.size();j++) {
              List<String> podIps = new ArrayList<>();
              JsonObject subset = subsets.getJsonObject(j);
              JsonArray addresses = subset.getJsonArray("addresses");
              JsonArray ports = subset.getJsonArray("ports");
              for (int k = 0;k < addresses.size();k++) {
                JsonObject address = addresses.getJsonObject(k);
                String ip = address.getString("ip");
                podIps.add(ip);
              }
              for (int k = 0;k < ports.size();k++) {
                JsonObject port = ports.getJsonObject(k);
                int podPort = port.getInteger("port");
                for (String podIp : podIps) {
                  SocketAddress podAddress = SocketAddress.inetSocketAddress(podPort, podIp);
                  podAddresses.add(podAddress);
                }
              }
            }
          }
        }
        return podAddresses;
      }).transform(ar -> {
        if (ar.succeeded()) {
          List<SocketAddress> res = ar.result();
          if (res.size() > 0) {
            return Future.succeededFuture(new ServiceState(res));
          } else {
            return Future.failedFuture("No results");
          }
        } else {
          return Future.failedFuture(ar.cause());
        }
      });
  }

  @Override
  public Future<SocketAddress> pickAddress(ServiceState unused) {
    return Future.succeededFuture(unused.addresses.get(0));
  }

  @Override
  public void removeAddress(ServiceState unused, SocketAddress socketAddress) {

  }

  @Override
  public void dispose(ServiceState unused) {

  }
}
