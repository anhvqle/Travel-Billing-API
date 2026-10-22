package com.travelbilling.transaction.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;

/**
 * The body of PATCH /transactions/{id}. Every field is optional: only the fields sent are changed.
 * tax_lines, fee_lines and metadata replace the stored ones whole.
 * type, external_id and currency cannot be changed; they are here only so we can refuse them with 400.
 */
public record UpdateTransactionRequest(
        String type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        JsonNode metadata) {
}
