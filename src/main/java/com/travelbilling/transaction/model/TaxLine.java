package com.travelbilling.transaction.model;

import java.math.BigDecimal;

import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.shared.Validation;
import com.travelbilling.shared.ValidationException;

/** A government tax on a transaction, e.g. VAT at rate 0.07. The rate is optional (airport tax has none). */
public record TaxLine(String name, BigDecimal rate, BigDecimal amount) {

    /** Checks the line is complete. `field` is its place in the request, e.g. "tax_lines[0]". */
    public void validate(String field) {
        Validation.requireText(name, field + ".name");
        if (rate != null && (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0)) {
            throw new ValidationException(field + ".rate must be between 0 and 1 (e.g. 0.07 for 7%)");
        }
        MoneyConverter.toCents(amount, field + ".amount");
    }
}
