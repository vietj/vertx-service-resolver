package io.vertx.serviceresolver.srv.impl;

import io.vertx.core.dns.SrvRecord;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.impl.ServiceState;

class SrvServiceState extends ServiceState<SrvRecord> {

  final long timestamp;

  SrvServiceState(String name, long timestamp) {
    super(name);

    this.timestamp = timestamp;
  }

  @Override
  protected boolean isValid() {
    long now = System.currentTimeMillis();
    for (SrvRecord endpoint : endpoints) {
      if (now > endpoint.ttl() * 1000 + timestamp) {
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
