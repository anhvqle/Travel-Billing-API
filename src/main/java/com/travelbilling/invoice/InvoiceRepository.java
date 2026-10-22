package com.travelbilling.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelbilling.invoice.model.Invoice;
import com.travelbilling.invoice.model.InvoiceLine;
import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;
import com.travelbilling.transaction.model.TransactionType;

/**
 * All SQL for invoices. Reads only the invoice tables, never the transactions table: that is what keeps
 * an invoice frozen. Writes must run inside the service's database transaction.
 */
@Repository
public class InvoiceRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public InvoiceRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Saves the invoice header, its snapshot lines and its tax/fee rollups. */
    public void insert(Invoice invoice) {
        jdbc.update("""
                INSERT INTO invoices (id, currency, total_cents, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                invoice.id(),
                invoice.currency(),
                MoneyConverter.toCents(invoice.totals().get("grand_total"), "grand_total"),
                invoice.createdAt().toString(),
                invoice.updatedAt().toString());
        insertLinesAndRollups(invoice);
    }

    private void insertLinesAndRollups(Invoice invoice) {
        String id = invoice.id();
        List<InvoiceLine> lines = invoice.lines();
        for (int i = 0; i < lines.size(); i++) {
            InvoiceLine line = lines.get(i);
            jdbc.update("""
                    INSERT INTO invoice_lines (invoice_id, position, transaction_id, type, total_cents, snapshot_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    id, i, line.transactionId(), line.type().value(),
                    MoneyConverter.toCents(line.total(), "total"), line.snapshot().toString());
        }
        List<TaxLine> taxRollup = invoice.taxRollup();
        for (int i = 0; i < taxRollup.size(); i++) {
            TaxLine tax = taxRollup.get(i);
            jdbc.update("""
                    INSERT INTO invoice_tax_rollup (invoice_id, position, name, rate, amount_cents)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    id, i, tax.name(), tax.rate() == null ? null : tax.rate().toPlainString(),
                    MoneyConverter.toCents(tax.amount(), "amount"));
        }
        List<FeeLine> feeRollup = invoice.feeRollup();
        for (int i = 0; i < feeRollup.size(); i++) {
            FeeLine fee = feeRollup.get(i);
            jdbc.update("""
                    INSERT INTO invoice_fee_rollup (invoice_id, position, name, amount_cents)
                    VALUES (?, ?, ?, ?)
                    """,
                    id, i, fee.name(), MoneyConverter.toCents(fee.amount(), "amount"));
        }
    }

    /** The invoice exactly as stored, or empty if the id does not exist. Per-kind totals are summed from the frozen lines. */
    public Optional<Invoice> findById(String id) {
        List<Header> headers = jdbc.query("""
                SELECT id, currency, created_at, updated_at FROM invoices WHERE id = ?
                """,
                (rs, rowNum) -> new Header(
                        rs.getString("id"),
                        rs.getString("currency"),
                        Instant.parse(rs.getString("created_at")),
                        Instant.parse(rs.getString("updated_at"))),
                id);
        if (headers.isEmpty()) {
            return Optional.empty();
        }
        Header header = headers.get(0);
        List<InvoiceLine> lines = findLines(id);
        return Optional.of(new Invoice(
                header.id(), header.currency(), lines, findTaxRollup(id), findFeeRollup(id),
                InvoiceCalculator.totalsByKind(lines), header.createdAt(), header.updatedAt()));
    }

    private record Header(String id, String currency, Instant createdAt, Instant updatedAt) {
    }

    private List<InvoiceLine> findLines(String id) {
        return jdbc.query("""
                SELECT transaction_id, type, total_cents, snapshot_json FROM invoice_lines
                WHERE invoice_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new InvoiceLine(
                        rs.getString("transaction_id"),
                        TransactionType.fromValue(rs.getString("type")).orElseThrow(),
                        MoneyConverter.fromCents(rs.getLong("total_cents")),
                        readSnapshot(rs.getString("snapshot_json"))),
                id);
    }

    private List<TaxLine> findTaxRollup(String id) {
        return jdbc.query("""
                SELECT name, rate, amount_cents FROM invoice_tax_rollup
                WHERE invoice_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new TaxLine(
                        rs.getString("name"),
                        rs.getString("rate") == null ? null : new BigDecimal(rs.getString("rate")),
                        MoneyConverter.fromCents(rs.getLong("amount_cents"))),
                id);
    }

    private List<FeeLine> findFeeRollup(String id) {
        return jdbc.query("""
                SELECT name, amount_cents FROM invoice_fee_rollup
                WHERE invoice_id = ? ORDER BY position
                """,
                (rs, rowNum) -> new FeeLine(rs.getString("name"), MoneyConverter.fromCents(rs.getLong("amount_cents"))),
                id);
    }

    private JsonNode readSnapshot(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("stored invoice snapshot is not valid JSON", e);
        }
    }
}
