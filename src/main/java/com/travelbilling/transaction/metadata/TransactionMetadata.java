package com.travelbilling.transaction.metadata;

import com.travelbilling.transaction.model.TransactionType;

/**
 * The type-specific part of a transaction ("metadata" in the API).
 * Sealed: exactly one implementation per TransactionType, and no others can be added by accident.
 */
public sealed interface TransactionMetadata
        permits FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata {

    /** Which transaction type this metadata belongs to. */
    TransactionType type();

    /** Throws ValidationException (HTTP 400) naming the first missing or bad field. */
    void validate();
}
