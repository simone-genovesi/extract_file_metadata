package com.simonegenovesi.extractorfiledata.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.io.IOException;

/**
 * Classe per la gestione centralizzata delle eccezioni nell'applicazione.
 * <p>
 * Utilizza l'annotazione {@link ControllerAdvice} per intercettare le eccezioni
 * sollevate dai controller e restituire risposte HTTP appropriate.
 */
@ControllerAdvice
@Slf4j
public class ExceptionManagement{

    /**
     * Gestisce tutte le eccezioni non catturate esplicitamente.
     * @param ex Eccezione generica
     * @return Messaggio di errore con stato HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ex.getMessage());
    }

    /**
     * Gestione specifica per problemi IO (es. lettura/scrittura file TIFF o thumbnail).
     * @param ex IOException
     * @return Risposta con errore specifico e stato HTTP 422
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException ex) {
       return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ex.getMessage());
    }

    /**
     * Gestione delle interruzioni nei thread durante l'elaborazione parallela.
     * @param ex InterruptedException
     * @return Risposta con messaggio di interruzione e stato HTTP 503
     */
    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<String> handleInterruptedException(InterruptedException ex) {
        Thread.currentThread().interrupt(); // ripristina stato di interruzione
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ex.getMessage());
    }

    /**
     * Gestione delle eccezioni personalizzate durante la generazione delle miniature.
     * @param ex ThumbnailProcessingException
     * @return Risposta con errore e stato HTTP 400
     */
    @ExceptionHandler(ThumbnailProcessingException.class)
    public ResponseEntity<String> handleThumbnailException(ThumbnailProcessingException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InvalidPathException.class)
    public ResponseEntity<String> handleInvalidPathException(InvalidPathException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(NoFilesFoundException.class)
    public ResponseEntity<String> handleNoFilesFoundException(NoFilesFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(DirectoryAlreadyProcessedException.class)
    public ResponseEntity<String> handleDirectoryAlreadyProcessedException(DirectoryAlreadyProcessedException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ex.getMessage());
    }

}
