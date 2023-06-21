package io.vertx.serviceresolver.impl.svc;

import io.vertx.core.Future;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.SrvRecord;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;
import io.vertx.serviceresolver.ServiceAddress;

import java.util.List;

public class SvcResolver implements AddressResolver<ServiceState, ServiceAddress, Void> {

  private VertxInternal vertx;
  final String host;
  final int port;
  final DnsClient client;

  public SvcResolver(VertxInternal vertx, String host, int port) {
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
    fut.map(records -> {

      for (SrvRecord record : records) {
        record.name();
      }

      return null;
    });

    return null;
  }

  @Override
  public Future<SocketAddress> pickAddress(ServiceState state) {
    return null;
  }

  @Override
  public void removeAddress(ServiceState state, SocketAddress address) {

  }

  @Override
  public void dispose(ServiceState state) {

  }
}
