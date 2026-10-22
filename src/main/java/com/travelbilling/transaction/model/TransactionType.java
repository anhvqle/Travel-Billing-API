package com.travelbilling.transaction.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** The four kinds of transaction the API accepts. */
public enum TransactionType {
    FLIGHT("flight"),
    HOTEL("hotel"),
    RAIL("rail"),
    NAVAN_FEE("navan_fee");

    private final String value;

    TransactionType(String value) {
        this.value = value;
    }

    /** The spelling used in JSON and in the database, e.g. "navan_fee". */
    @JsonValue
    public String value() {
        return value;
    }

    /** Finds the type for a JSON/database value, or empty if it is not one we know (e.g. "boat"). */
    public static Optional<TransactionType> fromValue(String value) {
        for (TransactionType type : values()) {
            if (type.value.equals(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
