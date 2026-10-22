package com.travelbilling.invoice.model;

import java.math.BigDecimal;
import java.time.Instant;

/** One row of GET /invoices: the header only, read in a single query. Full invoice via GET /invoices/{id}. */
public record InvoiceSummary(String id, String currency, BigDecimal total, Instant createdAt, Instant updatedAt) {
}
