package com.travelbilling.shared;

/** The request is invalid (missing or bad field). The error handler turns it into HTTP 400. */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
