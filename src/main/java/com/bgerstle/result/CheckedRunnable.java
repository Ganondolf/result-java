package com.bgerstle.result;

@FunctionalInterface
public interface CheckedRunnable<E extends Exception> {

    void run() throws E;

}
