package io.vertx.serviceresolver;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.Address;

@VertxGen
public interface ServiceAddress extends Address {

  static ServiceAddress create(String name) {
    if (name == null) {
      throw new NullPointerException();
    }
    return new ServiceAddress() {
      @Override
      public String name() {
        return name;
      }
      @Override
      public boolean equals(Object obj) {
        if (obj == this) {
          return true;
        }
        if (obj instanceof ServiceAddress) {
          ServiceAddress that = (ServiceAddress) obj;
          return name.equals(that.name());
        }
        return super.equals(obj);
      }
      @Override
      public int hashCode() {
        return name.hashCode();
      }
    };
  }

  String name();

}
