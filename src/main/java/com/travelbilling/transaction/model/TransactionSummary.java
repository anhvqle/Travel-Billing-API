package com.travelbilling.transaction.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of GET /transactions: the header only, read in a single query. Full details via GET /transactions/{id}. */
public record TransactionSummary(String id, String externalId, TransactionType type, String currency,
                                 Instant occurredAt, BigDecimal total, Instant createdAt) {
}
