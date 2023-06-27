package io.vertx.serviceresolver.impl;

import io.vertx.serviceresolver.Endpoint;

public class EndpointBase<V> implements Endpoint<V> {

  final V value;

  public EndpointBase(V value) {
    this.value = value;
  }

  @Override
  public V get() {
    return value;
  }
}
