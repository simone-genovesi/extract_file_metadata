package com.simonegenovesi.extractorfiledata.exceptions;

/**
 * Eccezione personalizzata lanciata durante errori nella generazione delle miniature.
 * Può essere usata per distinguere gli errori legati al dominio di elaborazione TIFF e immagini.
 */
public class ThumbnailProcessingException extends RuntimeException {

    /**
     * Costruttore con solo messaggio
     * @param message descrizione dell'errore
     */
    public ThumbnailProcessingException(String message) {
        super(message);
    }

    /**
     * Costruttore con messaggio e causa
     * @param message descrizione dell'errore
     * @param cause causa originale dell'errore
     */
    public ThumbnailProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
