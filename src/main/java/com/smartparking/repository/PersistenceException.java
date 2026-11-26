package com.smartparking.repository;

/** Unchecked wrapper for JDBC failures, so the service layer does not deal with SQLException. */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
