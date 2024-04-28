package com.scienceminer.glutton.exception;

/**
 * This class represent an exception when the data content causes some problems.
 * Parsing, extraction, null values.
 */
public class DataException extends RuntimeException {

    public DataException() {
        super();
    }

    public DataException(String message) {
        super(message);
    }

    public DataException(Throwable cause) {
        super(cause);
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }

}
