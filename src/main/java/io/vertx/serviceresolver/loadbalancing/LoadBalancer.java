package io.vertx.serviceresolver.loadbalancing;

import io.vertx.serviceresolver.Endpoint;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public interface LoadBalancer {

  LoadBalancer ROUND_ROBIN = () -> {
    AtomicInteger idx = new AtomicInteger();
    return new EndpointSelector() {
      @Override
      public <T, E extends Endpoint<T>> E selectEndpoint(List<E> endpoints) {
        int next = idx.getAndIncrement();
        return endpoints.get(next % endpoints.size());
      }
    };
  };

  EndpointSelector selector();

}
