package com.smartparking.service;

/** A business rule was broken: bad input, no free slot, unknown vehicle, and so on. */
public class ParkingException extends RuntimeException {

    public ParkingException(String message) {
        super(message);
    }

    public ParkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
