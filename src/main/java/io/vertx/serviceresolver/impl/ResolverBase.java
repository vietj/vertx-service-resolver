package io.vertx.serviceresolver.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.Address;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.resolver.AddressResolver;
import io.vertx.serviceresolver.ServiceAddress;
import io.vertx.serviceresolver.loadbalancing.LoadBalancer;

public abstract class ResolverBase<T extends ServiceState<?>> implements AddressResolver<T, ServiceAddress, Void> {

  protected final Vertx vertx;
  protected final LoadBalancer loadBalancer;

  public ResolverBase(Vertx vertx) {
    this.vertx = vertx;
    this.loadBalancer = LoadBalancer.ROUND_ROBIN;
  }

  @Override
  public ServiceAddress tryCast(Address address) {
    return address instanceof ServiceAddress ? (ServiceAddress) address : null;
  }

  @Override
  public Future<SocketAddress> pickAddress(T unused) {
    return unused.pickAddress();
  }

  @Override
  public boolean isValid(T state) {
    return state.isValid();
  }
}
