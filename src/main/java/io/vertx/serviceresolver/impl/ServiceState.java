package io.vertx.serviceresolver.impl;

import io.vertx.core.Future;
import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ServiceState<E> {

  public final String name;
  public final List<E> endpoints = new ArrayList<>();
  public final AtomicInteger idx = new AtomicInteger();

  public ServiceState(String name) {
    this.name = name;
  }

  Future<SocketAddress> pickAddress() {
    if (endpoints.isEmpty()) {
      return Future.failedFuture("No addresses for service " + name);
    } else {
      int next = idx.getAndIncrement();
      E endpoint = endpoints.get(next % endpoints.size());
      System.out.println("Picked addresse " + next);
      return Future.succeededFuture(toSocketAddress(endpoint));
    }
  }

  protected boolean isValid() {
    return true;
  }

  protected abstract SocketAddress toSocketAddress(E endpoint);



}
