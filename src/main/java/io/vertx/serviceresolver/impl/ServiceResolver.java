package io.vertx.serviceresolver.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;
import io.vertx.serviceresolver.ServiceAddress;

import static io.vertx.core.http.HttpMethod.GET;

public class ServiceResolver implements AddressResolver<ServiceState, ServiceAddress, Void> {

  final Vertx vertx;
  final String host;
  final int port;
  final HttpClient client;
  final String namespace;

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
      .request(GET, port, host, "/api/v1/namespaces/" + namespace + "/endpoints")
      .compose(req -> req.send().compose(resp -> {
        if (resp.statusCode() == 200) {
          return resp
            .body()
            .map(Buffer::toJsonObject);
        } else {
          return Future.failedFuture("Invalid status code " + resp.statusCode());
        }
      })).map(response -> {
        String resourceVersion = response.getJsonObject("metadata").getString("resourceVersion");
        ServiceState state = new ServiceState(this, resourceVersion, serviceName.name());
        JsonArray items = response.getJsonArray("items");
        for (int i = 0;i < items.size();i++) {
          JsonObject item = items.getJsonObject(i);
          if ("Endpoints".equals(item.getString("kind"))) {
            state.handleEndpoints(item);
          }
        }
        return state;
      }).andThen(ar -> {
        if (ar.succeeded()) {
          ServiceState res = ar.result();
          res.connectWebSocket();
        }
      });
  }

  @Override
  public Future<SocketAddress> pickAddress(ServiceState unused) {
    if (unused.podAddresses.isEmpty()) {
      return Future.failedFuture("No addresses");
    } else {
      int idx = unused.idx.getAndIncrement();
      SocketAddress address = unused.podAddresses.get(idx % unused.podAddresses.size());
      return Future.succeededFuture(address);
    }
  }

  @Override
  public void removeAddress(ServiceState unused, SocketAddress socketAddress) {

  }

  @Override
  public void dispose(ServiceState unused) {
    unused.disposed = true;
    if (unused.ws != null) {
      System.out.println("CLOSING WS");
      unused.ws.close();
    }
  }
}
