package io.vertx.serviceresolver.impl;

import io.vertx.core.net.SocketAddress;

import java.util.List;

public class ServiceState {

  final List<SocketAddress> addresses;

  public ServiceState(List<SocketAddress> addresses) {
    this.addresses = addresses;
  }
}
