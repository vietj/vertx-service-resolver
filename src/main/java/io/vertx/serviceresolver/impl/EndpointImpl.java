package io.vertx.serviceresolver.impl;

import io.vertx.serviceresolver.Endpoint;

final class EndpointImpl<V> implements Endpoint<V> {

  final V value;

  public EndpointImpl(V value) {
    this.value = value;
  }

  @Override
  public V get() {
    return value;
  }
}
