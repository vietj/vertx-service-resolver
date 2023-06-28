package io.vertx.serviceresolver.srv.impl;

import io.vertx.core.dns.SrvRecord;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.Endpoint;
import io.vertx.serviceresolver.impl.ServiceState;
import io.vertx.serviceresolver.loadbalancing.LoadBalancer;

class SrvServiceState extends ServiceState<SrvRecord> {

  final long timestamp;

  SrvServiceState(String name, long timestamp, LoadBalancer loadBalancer) {
    super(name, loadBalancer);

    this.timestamp = timestamp;
  }

  @Override
  protected boolean isValid() {
    long now = System.currentTimeMillis();
    for (Endpoint<SrvRecord> endpoint : endpoints) {
      if (now > endpoint.get().ttl() * 1000 + timestamp) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected SocketAddress toSocketAddress(SrvRecord endpoint) {
    return SocketAddress.inetSocketAddress(endpoint.port(), endpoint.target());
  }
}
