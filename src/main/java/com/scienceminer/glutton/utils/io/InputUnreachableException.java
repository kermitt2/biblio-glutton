package com.scienceminer.glutton.utils.io;

import java.io.IOException;

/**
 * An input that stayed out of reach for as long as it was asked for again: a network or a store
 * that is away, as opposed to a file that is wrong. A loader reading many files can tell from it
 * that trying the next one would only wait as long to fail the same way.
 */
public class InputUnreachableException extends IOException {

    public InputUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }
}
