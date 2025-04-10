package com.simonegenovesi.extractorfiledata.exceptions;

public class DirectoryAlreadyProcessedException extends RuntimeException {
    public DirectoryAlreadyProcessedException(String message) {
        super(message);
    }

    public DirectoryAlreadyProcessedException(String message, Throwable cause) {
        super(message, cause);
    }
}
