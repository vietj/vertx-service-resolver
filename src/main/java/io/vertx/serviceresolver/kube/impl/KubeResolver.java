package io.vertx.serviceresolver.kube.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.ServiceAddress;
import io.vertx.serviceresolver.impl.ResolverBase;

import static io.vertx.core.http.HttpMethod.GET;

public class KubeResolver extends ResolverBase<KubeServiceState> {

  final String host;
  final int port;
  final HttpClient client;
  final String namespace;
  final String bearerToken;

  public KubeResolver(Vertx vertx, String namespace, String host, int port, String bearerToken) {
    super(vertx);
    this.namespace = namespace;
    this.host = host;
    this.port = port;
    this.bearerToken = bearerToken;
    this.client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(true)
      .setTrustAll(true));
  }

  @Override
  public Future<KubeServiceState> resolve(ServiceAddress serviceName) {
    return client
      .request(GET, port, host, "/api/v1/namespaces/" + namespace + "/endpoints")
      .compose(req -> {
        if (bearerToken != null) {
          req.putHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken); // Todo concat that ?
        }
        return req.send().compose(resp -> {
          if (resp.statusCode() == 200) {
            return resp
              .body()
              .map(Buffer::toJsonObject);
          } else {
            return Future.failedFuture("Invalid status code " + resp.statusCode());
          }
        });
      }).map(response -> {
        String resourceVersion = response.getJsonObject("metadata").getString("resourceVersion");
        KubeServiceState state = new KubeServiceState(this, vertx, resourceVersion, serviceName.name(), loadBalancer);
        JsonArray items = response.getJsonArray("items");
        for (int i = 0;i < items.size();i++) {
          JsonObject item = items.getJsonObject(i);
          state.handleEndpoints(item);
        }
        return state;
      }).andThen(ar -> {
        if (ar.succeeded()) {
          KubeServiceState res = ar.result();
          res.connectWebSocket();
        }
      });
  }

  @Override
  public void removeAddress(KubeServiceState unused, SocketAddress socketAddress) {

  }

  @Override
  public void dispose(KubeServiceState unused) {
    unused.disposed = true;
    if (unused.ws != null) {
      unused.ws.close();
    }
  }
}
