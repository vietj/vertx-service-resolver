package io.vertx.serviceresolver.impl;

import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ServiceState {

  String lastResourceVersion;
  final String name;
  final List<SocketAddress> podAddresses;
  final AtomicInteger idx = new AtomicInteger();
  boolean disposed;
  WebSocket ws;

  ServiceState(String lastResourceVersion, String name) {
    this.lastResourceVersion = lastResourceVersion;
    this.name = name;
    this.podAddresses = new ArrayList<>();
  }

  void handleUpdate(JsonObject update) {
    String type = update.getString("type");
    JsonObject object = update.getJsonObject("object");
    if ("Endpoints".equals(object.getString("kind"))) {
      String resourceVersion = object.getJsonObject("metadata").getString("resourceVersion");
      if (!lastResourceVersion.equals(resourceVersion)) {
        handleEndpoints(object);
      }
    }
  }

  void handleEndpoints(JsonObject item) {
    podAddresses.clear();
    JsonObject metadata = item.getJsonObject("metadata");
    String name = metadata.getString("name");
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
