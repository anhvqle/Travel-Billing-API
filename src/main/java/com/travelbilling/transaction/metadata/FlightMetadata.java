package com.travelbilling.transaction.metadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.shared.Validation;
import com.travelbilling.shared.ValidationException;
import com.travelbilling.transaction.model.TransactionType;

/** A flight ticket: overall origin/destination, base fare, and one coupon per flown segment. */
public record FlightMetadata(String origin, String destination, BigDecimal fare, List<Coupon> coupons)
        implements TransactionMetadata {

    /** One flown segment, e.g. BA982 from TXL to LHR. The carrier is optional. */
    public record Coupon(String flightNumber, String carrier, String from, String to,
                         Instant departure, Instant arrival) {
    }

    @Override
    public TransactionType type() {
        return TransactionType.FLIGHT;
    }

    @Override
    public void validate() {
        Validation.requireText(origin, "metadata.origin");
        Validation.requireText(destination, "metadata.destination");
        MoneyConverter.toCents(fare, "metadata.fare");
        Validation.requirePresent(coupons, "metadata.coupons");
        if (coupons.isEmpty()) {
            throw new ValidationException("metadata.coupons must contain at least one coupon");
        }
        for (int i = 0; i < coupons.size(); i++) {
            String field = "metadata.coupons[" + i + "]";
            Coupon coupon = Validation.requirePresent(coupons.get(i), field);
            Validation.requireText(coupon.flightNumber(), field + ".flight_number");
            Validation.requireText(coupon.from(), field + ".from");
            Validation.requireText(coupon.to(), field + ".to");
            Validation.requirePresent(coupon.departure(), field + ".departure");
            Validation.requirePresent(coupon.arrival(), field + ".arrival");
            if (!coupon.arrival().isAfter(coupon.departure())) {
                throw new ValidationException(field + ".arrival must be after departure");
            }
        }
    }
}
