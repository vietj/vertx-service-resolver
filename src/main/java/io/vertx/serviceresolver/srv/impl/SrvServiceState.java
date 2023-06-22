package io.vertx.serviceresolver.srv.impl;

import io.vertx.core.dns.SrvRecord;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.impl.ServiceState;

class SrvServiceState extends ServiceState<SrvRecord> {

  SrvServiceState(String name) {
    super(name);
  }

  @Override
  protected SocketAddress toSocketAddress(SrvRecord endpoint) {
    return SocketAddress.inetSocketAddress(endpoint.port(), endpoint.target());
  }
}
