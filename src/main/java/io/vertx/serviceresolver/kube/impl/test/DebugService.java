package io.vertx.serviceresolver.kube.impl.test;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.HttpClientInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceresolver.ServiceAddress;
import io.vertx.serviceresolver.kube.impl.KubeResolver;

import java.io.File;

public class DebugService extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx
      .deployVerticle(new DebugService())
      .onComplete(ar -> {
        if (ar.succeeded()) {
          System.out.println("Service started");
        } else {
          System.out.println("Start failure:");
          ar.cause().printStackTrace(System.out);
        }
    });
  }

  private static final String KUBERNETES_SERVICE_HOST = "KUBERNETES_SERVICE_HOST";
  private static final String KUBERNETES_SERVICE_PORT = "KUBERNETES_SERVICE_PORT";
  private static final String KUBERNETES_SERVICE_ACCOUNT_TOKEN = "/var/run/secrets/kubernetes.io/serviceaccount/token";
  private static final String KUBERNETES_SERVICE_ACCOUNT_CA = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
  private HttpClient client;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

/*
    String host = System.getenv(KUBERNETES_SERVICE_HOST);
    String port = System.getenv(KUBERNETES_SERVICE_PORT);
    File tokenFile = new File(KUBERNETES_SERVICE_ACCOUNT_TOKEN);
    File ca = new File(KUBERNETES_SERVICE_ACCOUNT_CA);
    Buffer token;
    JsonObject jwt;
    if (tokenFile.exists()) {
      token = vertx.fileSystem().readFileBlocking(KUBERNETES_SERVICE_ACCOUNT_TOKEN);
//      byte[] decoded = Base64.getDecoder().decode(token.toString());
//      jwt = new JsonObject(Buffer.buffer(decoded));
      jwt = null;
    } else {
      token = null;
      jwt = null;
    }
*/

/*
    client = vertx.createHttpClient(new HttpClientOptions()
      .setSsl(false)
      .setTrustAll(true));
    ((HttpClientInternal)client).addressResolver(new KubeResolver(vertx, "default", host, Integer.parseInt(port), token.toString()));

    ServiceAddress sa = ServiceAddress.create("hello-node");
*/

    vertx.createHttpServer().requestHandler(req -> {
/*
        Future<HttpClientRequest> fut = ((HttpClientInternal) client).request(sa, HttpMethod.GET, 80, "localhost", "/");
        fut.compose(r -> {
          return r.send().compose(resp -> resp.body().map(body -> {
            return "Hello from: " + r.connection().remoteAddress() + " with: " + body;
          }));
        }).onComplete(ar -> {
          if (ar.succeeded()) {
            req.response()
              .putHeader("content-type", "text/plain")
              .end(ar.result());
          } else {
            req.response()
              .putHeader("content-type", "text/plain")
              .end("Error " + ar.cause().getMessage());
          }
        });

*/
      req.response().end("Hello World from Vert.x");
      }).listen(8080)
      .onComplete(http -> {
        if (http.succeeded()) {
          startPromise.complete();
          System.out.println("HTTP server started on port 8080");
        } else {
          startPromise.fail(http.cause());
        }
      });
  }
}
