package io.vertx.serviceresolver.srv.impl;

import io.vertx.core.Future;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.serviceresolver.ServiceAddress;
import io.vertx.serviceresolver.impl.ResolverBase;

import java.util.List;

public class SrvResolver extends ResolverBase<SrvServiceState> {

  final String host;
  final int port;
  final DnsClient client;

  public SrvResolver(VertxInternal vertx, String host, int port) {
    super(vertx);
    this.host = host;
    this.port = port;
    this.client = vertx.createDnsClient(port, host);
  }

  @Override
  public ServiceAddress tryCast(Address address) {
    return address instanceof ServiceAddress ? (ServiceAddress) address : null;
  }

  @Override
  public Future<SrvServiceState> resolve(ServiceAddress address) {
    Future<List<SrvRecord>> fut = client.resolveSRV(address.name());
    return fut.map(records -> {
      SrvServiceState state = new SrvServiceState(address.name(), System.currentTimeMillis());
      state.endpoints.addAll(records);
      return state;
    });
  }

  @Override
  public void removeAddress(SrvServiceState state, SocketAddress address) {
    // TODO
  }

  @Override
  public void dispose(SrvServiceState state) {
    // TODO
  }
}
