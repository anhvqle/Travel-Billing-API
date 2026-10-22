package com.travelbilling.invoice.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;

/**
 * An invoice as GET /invoices/{id} returns it: built only from its own snapshot tables, never from live transactions.
 * tax_rollup groups taxes by (name, rate); fee_rollup groups fees by name.
 * totals has one entry per kind of line (flight, hotel, rail, trip_fee, agent_call_fee) plus grand_total.
 */
public record Invoice(
        String id,
        String currency,
        List<InvoiceLine> lines,
        List<TaxLine> taxRollup,
        List<FeeLine> feeRollup,
        Map<String, BigDecimal> totals,
        Instant createdAt,
        Instant updatedAt) {
}
