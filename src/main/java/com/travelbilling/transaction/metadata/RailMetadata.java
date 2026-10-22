package com.travelbilling.transaction.metadata;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.travelbilling.shared.Validation;
import com.travelbilling.shared.ValidationException;
import com.travelbilling.transaction.model.TransactionType;

/** A rail journey as one or more legs. A journey with a change of train is two legs. */
public record RailMetadata(List<Leg> legs) implements TransactionMetadata {

    /** One train ride. Train number and class are optional. "class" is a reserved word in Java, hence travelClass. */
    public record Leg(String origin, String destination, Instant departure, Instant arrival,
                      String trainNumber, @JsonProperty("class") String travelClass) {
    }

    @Override
    public TransactionType type() {
        return TransactionType.RAIL;
    }

    @Override
    public void validate() {
        Validation.requirePresent(legs, "metadata.legs");
        if (legs.isEmpty()) {
            throw new ValidationException("metadata.legs must contain at least one leg");
        }
        for (int i = 0; i < legs.size(); i++) {
            String field = "metadata.legs[" + i + "]";
            Leg leg = Validation.requirePresent(legs.get(i), field);
            Validation.requireText(leg.origin(), field + ".origin");
            Validation.requireText(leg.destination(), field + ".destination");
            Validation.requirePresent(leg.departure(), field + ".departure");
            Validation.requirePresent(leg.arrival(), field + ".arrival");
            if (!leg.arrival().isAfter(leg.departure())) {
                throw new ValidationException(field + ".arrival must be after departure");
            }
        }
    }
}
