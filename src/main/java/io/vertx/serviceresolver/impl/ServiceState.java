package io.vertx.serviceresolver.impl;

import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.Endpoint;
import io.vertx.serviceresolver.loadbalancing.EndpointSelector;
import io.vertx.serviceresolver.loadbalancing.LoadBalancer;

import java.util.ArrayList;
import java.util.List;

public abstract class ServiceState<E> {

  public final String name;
  public final List<EndpointBase<E>> endpoints = new ArrayList<>();
  private final EndpointSelector selector;

  public ServiceState(String name, LoadBalancer loadBalancer) {
    this.name = name;
    this.selector = loadBalancer.selector();
  }

  Future<SocketAddress> pickAddress() {
    if (endpoints.isEmpty()) {
      return Future.failedFuture("No addresses for service " + name);
    } else {
      EndpointBase<E> endpoint = selector.selectEndpoint(endpoints);
      return Future.succeededFuture(toSocketAddress(endpoint.get()));
    }
  }

  public final void add(E endpoint) {
    endpoints.add(new EndpointBase<>(endpoint));
  }

  public final void add(List<E> endpoints) {
    for (E endpoint : endpoints) {
      add(endpoint);
    }
  }

  protected boolean isValid() {
    return true;
  }

  protected abstract SocketAddress toSocketAddress(E endpoint);



}
