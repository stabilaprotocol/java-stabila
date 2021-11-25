package org.stabila.core.capsule;

public interface ProtoCapsule<T> {

  byte[] getData();

  T getInstance();
}
