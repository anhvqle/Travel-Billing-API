package com.travelbilling.shared;

/** Small helpers for "this field must be present" checks. Each failure becomes a 400 naming the field. */
public final class Validation {

    private Validation() {
    }

    /** The value must be non-blank text, e.g. a name or a city. */
    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field + " is required");
        }
        return value;
    }

    /** The value must be present (not null), e.g. a date or a list. */
    public static <T> T requirePresent(T value, String field) {
        if (value == null) {
            throw new ValidationException(field + " is required");
        }
        return value;
    }
}
