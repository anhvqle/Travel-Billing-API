package com.travelbilling.shared;

import java.math.BigDecimal;

/**
 * Money is stored as whole cents (a long) with a fixed scale of 2: 220.00 is stored as 22000.
 * BigDecimal is used at the API edge so no amount ever goes through floating point.
 */
public final class MoneyConverter {

    public static final int SCALE = 2;

    private MoneyConverter() {
    }

    /** 220.00 -> 22000. Rejects amounts with more than 2 decimals (e.g. 1.005) instead of silently rounding. */
    public static long toCents(BigDecimal amount, String field) {
        if (amount == null) {
            throw new ValidationException(field + " is required");
        }
        try {
            return amount.setScale(SCALE).unscaledValue().longValueExact();
        } catch (ArithmeticException e) {
            throw new ValidationException(field + " must have at most " + SCALE + " decimal places");
        }
    }

    /** 22000 -> 220.00 */
    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE);
    }
}
