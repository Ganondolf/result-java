package com.bgerstle.result;

@FunctionalInterface
public interface CheckedSupplier<V, E extends Exception> {

    V get() throws E;

}
