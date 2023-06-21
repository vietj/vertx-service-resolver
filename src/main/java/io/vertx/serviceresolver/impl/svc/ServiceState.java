package io.vertx.serviceresolver.impl.svc;

import io.vertx.core.net.SocketAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ServiceState {

  final String name;
  final List<SocketAddress> podAddresses;
  final AtomicInteger idx = new AtomicInteger();

  ServiceState(String name) {
    this.name = name;
    this.podAddresses = new ArrayList<>();
  }
}
