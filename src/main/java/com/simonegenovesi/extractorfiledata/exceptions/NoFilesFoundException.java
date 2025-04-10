package com.simonegenovesi.extractorfiledata.exceptions;

/**
 * Eccezione lanciata quando non vengono trovati file validi nelle directory.
 */
public class NoFilesFoundException extends RuntimeException {
    public NoFilesFoundException(String message) {
        super(message);
    }

    public NoFilesFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
