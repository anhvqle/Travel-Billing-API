package com.travelbilling.shared;

/**
 * The request conflicts with what is stored, e.g. a duplicate external_id or mixed currencies.
 * The error handler turns it into HTTP 409. existingId is set when the conflict is with a stored record
 * (e.g. the transaction that already has this external_id), so the client can find it.
 */
public class ConflictException extends RuntimeException {

    private final String existingId;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String existingId) {
        super(message);
        this.existingId = existingId;
    }

    public String existingId() {
        return existingId;
    }
}
