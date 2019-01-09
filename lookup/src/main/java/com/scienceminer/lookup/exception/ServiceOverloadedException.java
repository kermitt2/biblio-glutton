package com.scienceminer.lookup.exception;

/**
 * This exception represent a state where the service is not able to respond all the requests and
 * will be converted to error code 503
 **/
public class ServiceOverloadedException extends RuntimeException {

    public ServiceOverloadedException() {
    }

    public ServiceOverloadedException(String message) {
        super(message);
    }

    public ServiceOverloadedException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
