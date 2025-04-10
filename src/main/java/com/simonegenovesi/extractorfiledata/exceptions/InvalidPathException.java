package com.simonegenovesi.extractorfiledata.exceptions;

/**
 * Eccezione lanciata quando il path fornito non è valido o non accessibile.
 */
public class InvalidPathException extends RuntimeException {
    public InvalidPathException(String message) {
        super(message);
    }

    public InvalidPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
