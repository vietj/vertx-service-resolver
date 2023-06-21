package io.vertx.serviceresolver.impl.srv;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;
import io.vertx.serviceresolver.ServiceAddress;

import java.util.List;

public class SrvResolver implements AddressResolver<ServiceState, ServiceAddress, Void> {

  private VertxInternal vertx;
  final String host;
  final int port;
  final DnsClient client;

  public SrvResolver(VertxInternal vertx, String host, int port) {
    this.vertx = vertx;
    this.host = host;
    this.port = port;
    this.client = vertx.createDnsClient(port, host);
  }

  @Override
  public ServiceAddress tryCast(Address address) {
    return address instanceof ServiceAddress ? (ServiceAddress) address : null;
  }

  @Override
  public Future<ServiceState> resolve(ServiceAddress address) {
    Future<List<SrvRecord>> fut = client.resolveSRV(address.name());
    return fut.map(records -> {
      ServiceState state = new ServiceState(address.name());
      state.podAddresses.addAll(records);
      return state;
    });
  }

  @Override
  public Future<SocketAddress> pickAddress(ServiceState state) {
    if (state.podAddresses.size() == 0) {
      return Future.failedFuture("No addresses");
    }
    int idx = state.idx.getAndIncrement();
    SrvRecord record = state.podAddresses.get(idx % state.podAddresses.size());
    return Future.succeededFuture(SocketAddress.inetSocketAddress(record.port(), record.target()));
  }

  @Override
  public void removeAddress(ServiceState state, SocketAddress address) {
    // TODO
  }

  @Override
  public void dispose(ServiceState state) {
    // TODO
  }
}
