package com.travelbilling.transaction.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.travelbilling.transaction.metadata.TransactionMetadata;

/**
 * A stored travel transaction, exactly as GET /transactions/{id} returns it.
 * id is ours (server-generated); external_id is the client's booking reference and is unique.
 */
public record Transaction(
        String id,
        String externalId,
        TransactionType type,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata,
        Instant createdAt,
        Instant updatedAt) {
}
