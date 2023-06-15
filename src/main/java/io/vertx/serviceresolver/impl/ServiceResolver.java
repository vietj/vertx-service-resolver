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
        ServiceState state = new ServiceState(resourceVersion, serviceName.name());
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
          String path = "/api/v1/namespaces/" + namespace + "/endpoints?"
            + "watch=true"
            + "&"
            + "allowWatchBookmarks=true"
            + "&"
            + "resourceVersion=" + res.lastResourceVersion;
          client.webSocket(port, host, path).onComplete(ar2 -> {
            if (ar2.succeeded()) {
              WebSocket ws = ar2.result();
              ws.handler(buff -> {
                JsonObject update  = buff.toJsonObject();
                res.handleUpdate(update);
              });
              ws.closeHandler(v -> {
                System.out.println("WEBSOCKET CLOSED HANDLE ME");
              });
            } else {
              System.out.println("WS upgrade failed");
            }
          });
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
    System.out.println("TODO DISPOSE");
  }
}
