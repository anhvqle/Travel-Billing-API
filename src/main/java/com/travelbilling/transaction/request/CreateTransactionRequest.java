package com.travelbilling.transaction.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;

/**
 * The body of POST /transactions, before validation. Everything may be missing or wrong here;
 * type is kept as text and metadata as raw JSON because metadata's shape depends on type.
 */
public record CreateTransactionRequest(
        String type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        JsonNode metadata) {
}
