package io.vertx.serviceresolver;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.Address;

@VertxGen
public interface ServiceName extends Address {

  static ServiceName create(String name) {
    throw new UnsupportedOperationException();
  }

  String name();

}
