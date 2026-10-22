package com.travelbilling.shared;

/** The requested thing does not exist. The error handler turns it into HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
