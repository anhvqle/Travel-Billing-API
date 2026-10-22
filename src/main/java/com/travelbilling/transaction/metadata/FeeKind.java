package com.travelbilling.transaction.metadata;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** The kinds of Navan-owned fee: a fee per trip, and a fee for a call to a support agent. */
public enum FeeKind {
    TRIP_FEE("trip_fee"),
    AGENT_CALL_FEE("agent_call_fee");

    private final String value;

    FeeKind(String value) {
        this.value = value;
    }

    /** The spelling used in JSON and in the database, e.g. "agent_call_fee". */
    @JsonValue
    public String value() {
        return value;
    }

    /** Finds the fee kind for a JSON/database value, or empty if it is not one we know. */
    public static Optional<FeeKind> fromValue(String value) {
        for (FeeKind kind : values()) {
            if (kind.value.equals(value)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
