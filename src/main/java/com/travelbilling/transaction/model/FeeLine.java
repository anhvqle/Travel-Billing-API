package com.travelbilling.transaction.model;

import java.math.BigDecimal;

import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.shared.Validation;

/** A carrier/merchant fee on a transaction, e.g. YQ surcharge or booking fee. Kept separate from taxes. */
public record FeeLine(String name, BigDecimal amount) {

    /** Checks the line is complete. `field` is its place in the request, e.g. "fee_lines[0]". */
    public void validate(String field) {
        Validation.requireText(name, field + ".name");
        MoneyConverter.toCents(amount, field + ".amount");
    }
}
