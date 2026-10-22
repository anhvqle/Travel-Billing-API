package com.travelbilling.invoice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.travelbilling.invoice.model.InvoiceLine;
import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;
import com.travelbilling.transaction.model.Transaction;
import com.travelbilling.transaction.model.TransactionType;

/**
 * The invoice math, with no database or HTTP: grand total, tax rollup grouped by (name, rate),
 * fee rollup grouped by name, and per-kind totals. All sums are done in whole cents, so they are exact.
 */
public final class InvoiceCalculator {

    private InvoiceCalculator() {
    }

    /** The tax and fee rollups, in the order each group first appears. */
    public record Rollups(List<TaxLine> taxRollup, List<FeeLine> feeRollup) {
    }

    /** Taxes with the same name but a different rate (VAT 7% vs VAT 19%) stay separate. */
    private record TaxGroup(String name, BigDecimal rate) {
    }

    public static Rollups calculate(List<Transaction> transactions) {
        Map<TaxGroup, Long> taxCents = new LinkedHashMap<>();
        Map<String, Long> feeCents = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            for (TaxLine tax : transaction.taxLines()) {
                BigDecimal rate = tax.rate() == null ? null : tax.rate().stripTrailingZeros();
                taxCents.merge(new TaxGroup(tax.name(), rate), MoneyConverter.toCents(tax.amount(), "amount"), Long::sum);
            }
            for (FeeLine fee : transaction.feeLines()) {
                feeCents.merge(fee.name(), MoneyConverter.toCents(fee.amount(), "amount"), Long::sum);
            }
        }

        List<TaxLine> taxRollup = new ArrayList<>();
        taxCents.forEach((group, cents) ->
                taxRollup.add(new TaxLine(group.name(), group.rate(), MoneyConverter.fromCents(cents))));
        List<FeeLine> feeRollup = new ArrayList<>();
        feeCents.forEach((name, cents) -> feeRollup.add(new FeeLine(name, MoneyConverter.fromCents(cents))));
        return new Rollups(taxRollup, feeRollup);
    }

    /**
     * Totals per kind of line plus grand_total, e.g. {flight: 220.00, hotel: 331.70, ..., grand_total: 685.70}.
     * Navan fees are split by fee_kind (trip_fee, agent_call_fee), as in expected-invoice.json.
     */
    public static Map<String, BigDecimal> totalsByKind(List<InvoiceLine> lines) {
        Map<String, Long> cents = new LinkedHashMap<>();
        long grandTotal = 0;
        for (InvoiceLine line : lines) {
            long lineCents = MoneyConverter.toCents(line.total(), "total");
            cents.merge(kindOf(line), lineCents, Long::sum);
            grandTotal += lineCents;
        }
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        cents.forEach((kind, value) -> totals.put(kind, MoneyConverter.fromCents(value)));
        totals.put("grand_total", MoneyConverter.fromCents(grandTotal));
        return totals;
    }

    private static String kindOf(InvoiceLine line) {
        if (line.type() == TransactionType.NAVAN_FEE) {
            return line.snapshot().path("metadata").path("fee_kind").asText();
        }
        return line.type().value();
    }
}
