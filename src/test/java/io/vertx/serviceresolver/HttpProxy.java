package io.vertx.serviceresolver;

import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.core.net.SocketAddress;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpProxy {

  private final Vertx vertx;
  private HttpServer server;
  private HttpClient client;
  private SocketAddress origin;
  private int port;
  private AtomicInteger pendingWebSockets = new AtomicInteger();

  public HttpProxy(Vertx vertx) {
    this.vertx = vertx;
    this.client = vertx.createHttpClient();
  }

  public HttpProxy origin(SocketAddress origin) {
    this.origin = origin;
    return this;
  }

  public HttpProxy port(int port) {
    this.port = port;
    return this;
  }

  public void start() throws Exception {
    HttpServer server = vertx.createHttpServer();
    server.requestHandler(this::handleRequest);
    server
      .listen(port)
      .toCompletionStage()
      .toCompletableFuture()
      .get(20, TimeUnit.SECONDS);
  }

  private void handleRequest(HttpServerRequest serverRequest) {
    if (serverRequest.getHeader("upgrade") != null) {
      WebSocketConnectOptions options = new WebSocketConnectOptions();
      options.setServer(origin);
      options.setURI(serverRequest.uri());
      serverRequest.pause();
      client.webSocket(options).onComplete(ar -> {
        if (ar.succeeded()) {
          WebSocket wsc = ar.result();
          AtomicBoolean closed = new AtomicBoolean();
          wsc.closeHandler(v -> {
            closed.set(true);
          });
          serverRequest.toWebSocket().onComplete(ar2 -> {
            if (!closed.get()) {
              if (ar2.succeeded()) {
                pendingWebSockets.incrementAndGet();
                ServerWebSocket wss = ar2.result();
                wsc.handler(wss::write);
                wss.endHandler(v -> {
                  wsc.end();
                });
                wsc.endHandler(v -> {
                  wss.end();
                });
                wss.closeHandler(v -> {
                  wsc.close();
                });
                wsc.closeHandler(v -> {
                  pendingWebSockets.decrementAndGet();
                  wss.close();
                });
              } else {
                wsc.close();
              }
            } else {
              if (ar2.succeeded()) {
                ar2.result().close();
              }
            }
          });
        } else {
          serverRequest.response().setStatusCode(500).end();
        }
      });

      System.out.println("handle upgrade");
    } else {
      RequestOptions options = new RequestOptions()
        .setServer(origin)
        .setMethod(serverRequest.method())
        .setURI(serverRequest.uri());
      serverRequest.body().onComplete(ar_ -> {
        if (ar_.succeeded()) {
          client.request(options).onComplete(ar -> {
            if (ar.succeeded()) {
              HttpClientRequest clientRequest = ar.result();
              clientRequest.send(ar_.result()).onComplete(ar2 -> {
                if (ar2.succeeded()) {
                  HttpClientResponse clientResponse = ar2.result();
                  HttpServerResponse serverResponse = serverRequest.response();
                  serverResponse.putHeader(HttpHeaders.CONTENT_LENGTH, clientResponse.getHeader(HttpHeaders.CONTENT_LENGTH));
                  clientResponse.pipeTo(serverResponse);
                } else {
                  serverRequest.response().setStatusCode(500).end();
                }
              });
            } else {
              serverRequest.response().reset();
            }
          });
        } else {
          // Nothing to do ? (compose?)
        }
      });
      System.out.println("handle request " + serverRequest.headers());
    }
  }

  public int pendingWebSockets() {
    return pendingWebSockets.get();
  }
}
