/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.serviceresolver.kube;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true, publicConverter = false)
public class KubeResolverOptions {

  private String host;
  private int port;
  private String namespace;
  private String bearerToken;
  private HttpClientOptions httpClientOptions;
  private WebSocketClientOptions webSocketClientOptions;

  public KubeResolverOptions() {
  }

  public KubeResolverOptions(KubeResolverOptions other) {
    this.host = other.host;
    this.port = other.port;
    this.namespace = other.namespace;
    this.bearerToken = other.bearerToken;
    this.httpClientOptions = other.httpClientOptions != null ? new HttpClientOptions(other.httpClientOptions) : null;
    this.webSocketClientOptions = other.webSocketClientOptions != null ? new WebSocketClientOptions(other.webSocketClientOptions) : null;
  }

  public KubeResolverOptions(JsonObject json) {
    KubeResolverOptionsConverter.fromJson(json, this);
  }

  public String getHost() {
    return host;
  }

  public KubeResolverOptions setHost(String host) {
    this.host = host;
    return this;
  }

  public int getPort() {
    return port;
  }

  public KubeResolverOptions setPort(int port) {
    this.port = port;
    return this;
  }

  public String getNamespace() {
    return namespace;
  }

  public KubeResolverOptions setNamespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public KubeResolverOptions setBearerToken(String bearerToken) {
    this.bearerToken = bearerToken;
    return this;
  }

  public HttpClientOptions getHttpClientOptions() {
    return httpClientOptions;
  }

  public KubeResolverOptions setHttpClientOptions(HttpClientOptions httpClientOptions) {
    this.httpClientOptions = httpClientOptions;
    return this;
  }

  public WebSocketClientOptions getWebSocketClientOptions() {
    return webSocketClientOptions;
  }

  public KubeResolverOptions setWebSocketClientOptions(WebSocketClientOptions webSocketClientOptions) {
    this.webSocketClientOptions = webSocketClientOptions;
    return this;
  }
}
