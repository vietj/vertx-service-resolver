package io.vertx.serviceresolver.impl.srv;

import io.vertx.core.dns.SrvRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ServiceState {

  final String name;
  final List<SrvRecord> podAddresses;
  final AtomicInteger idx = new AtomicInteger();

  ServiceState(String name) {
    this.name = name;
    this.podAddresses = new ArrayList<>();
  }
}
