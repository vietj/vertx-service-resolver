package io.vertx.serviceresolver.kube.impl;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.impl.ServiceState;

import java.util.ArrayList;
import java.util.List;

class KubeServiceState extends ServiceState<SocketAddress> {

  String lastResourceVersion;
  final Vertx vertx;
  final KubeResolver resolver;
  boolean disposed;
  WebSocket ws;

  KubeServiceState(KubeResolver resolver, Vertx vertx, String lastResourceVersion, String name) {
    super(name);
    this.resolver = resolver;
    this.vertx = vertx;
    this.lastResourceVersion = lastResourceVersion;
  }

  void connectWebSocket() {
    String requestURI = "/api/v1/namespaces/" + resolver.namespace + "/endpoints?"
      + "watch=true"
      + "&"
      + "allowWatchBookmarks=true"
      + "&"
      + "resourceVersion=" + lastResourceVersion;
    WebSocketConnectOptions connectOptions = new WebSocketConnectOptions();
    connectOptions.setHost(resolver.host);
    connectOptions.setPort(resolver.port);
    connectOptions.setURI(requestURI);
    if (resolver.bearerToken != null) {
      connectOptions.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resolver.bearerToken);
    }
    resolver.client.webSocket(connectOptions).onComplete(ar2 -> {
      if (ar2.succeeded()) {
        WebSocket ws = ar2.result();
        if (disposed) {
          ws.close();
        } else {
          this.ws = ws;
          ws.handler(buff -> {
            JsonObject update  = buff.toJsonObject();
            handleUpdate(update);
          });
          ws.closeHandler(v -> {
            if (!disposed) {
              connectWebSocket();
            }
          });
        }
      } else {
        if (!disposed) {
          // Retry WebSocket connect
          vertx.setTimer(500, id -> {
            connectWebSocket();
          });
        }
      }
    });
  }

  @Override
  protected SocketAddress toSocketAddress(SocketAddress endpoint) {
    return endpoint;
  }

  void handleUpdate(JsonObject update) {
    String type = update.getString("type");
    JsonObject object = update.getJsonObject("object");
    JsonObject metadata = object.getJsonObject("metadata");
    String resourceVersion = metadata.getString("resourceVersion");
    if (!lastResourceVersion.equals(resourceVersion)) {
      handleEndpoints(object);
    }
  }

  void handleEndpoints(JsonObject item) {
    JsonObject metadata = item.getJsonObject("metadata");
    String name = metadata.getString("name");
    if (this.name.equals(name)) {
      endpoints.clear();
      JsonArray subsets = item.getJsonArray("subsets");
      if (subsets != null) {
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
              endpoints.add(podAddress);
            }
          }
        }
      }
    }
  }
}
