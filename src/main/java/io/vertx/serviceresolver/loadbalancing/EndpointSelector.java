package io.vertx.serviceresolver.loadbalancing;

import io.vertx.serviceresolver.Endpoint;

import java.util.List;

public interface EndpointSelector {

  <T, E extends Endpoint<T>> E selectEndpoint(List<E> endpoints);

}
