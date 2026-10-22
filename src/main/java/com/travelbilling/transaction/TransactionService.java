package com.travelbilling.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.travelbilling.shared.ConflictException;
import com.travelbilling.shared.IdGenerator;
import com.travelbilling.shared.MoneyConverter;
import com.travelbilling.shared.NotFoundException;
import com.travelbilling.shared.Page;
import com.travelbilling.shared.Pagination;
import com.travelbilling.shared.Validation;
import com.travelbilling.shared.ValidationException;
import com.travelbilling.transaction.metadata.TransactionMetadata;
import com.travelbilling.transaction.model.FeeLine;
import com.travelbilling.transaction.model.TaxLine;
import com.travelbilling.transaction.model.Transaction;
import com.travelbilling.transaction.model.TransactionSummary;
import com.travelbilling.transaction.model.TransactionType;
import com.travelbilling.transaction.request.CreateTransactionRequest;
import com.travelbilling.transaction.request.UpdateTransactionRequest;

/** Business rules for transactions: identity (no duplicate external_id), validation, and atomic writes. */
@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final MetadataParser metadataParser;

    public TransactionService(TransactionRepository repository, MetadataParser metadataParser) {
        this.repository = repository;
        this.metadataParser = metadataParser;
    }

    /**
     * Creates a transaction with all its lines and details in one database transaction.
     * Identity is checked before the body is validated, so a retry of a booking we already have
     * always gets 409 with the existing id.
     */
    @Transactional
    public Transaction create(CreateTransactionRequest request) {
        String externalId = Validation.requireText(request.externalId(), "external_id");
        Optional<String> existingId = repository.findIdByExternalId(externalId);
        if (existingId.isPresent()) {
            throw duplicate(externalId, existingId.get());
        }

        Transaction transaction = buildValidTransaction(request, externalId);

        // The unique index on external_id is the real guard: if another request inserted the same
        // external_id after our check above, insert() saves nothing and returns false.
        if (!repository.insert(transaction)) {
            throw duplicate(externalId, repository.findIdByExternalId(externalId).orElseThrow());
        }
        return repository.findById(transaction.id()).orElseThrow();
    }

    @Transactional
    public Transaction get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("transaction " + id + " not found"));
    }

    /**
     * Changes the fields sent and leaves the rest. Refused with 409 if the transaction is on any invoice,
     * so billed money is never rewritten. Everything happens in one database transaction.
     */
    @Transactional
    public Transaction update(String id, UpdateTransactionRequest request) {
        Transaction current = get(id);
        if (repository.isOnAnyInvoice(id)) {
            throw new ConflictException("transaction " + id + " is on an invoice and cannot be changed");
        }
        if (request.type() != null || request.externalId() != null || request.currency() != null) {
            throw new ValidationException("type, external_id and currency cannot be changed");
        }
        if (request.occurredAt() == null && request.total() == null && request.taxLines() == null
                && request.feeLines() == null && request.metadata() == null) {
            throw new ValidationException(
                    "nothing to update: send at least one of occurred_at, total, tax_lines, fee_lines, metadata");
        }

        BigDecimal total = request.total() != null ? request.total() : current.total();
        MoneyConverter.toCents(total, "total");
        TransactionMetadata metadata = current.metadata();
        if (request.metadata() != null) {
            metadata = metadataParser.parse(current.type(), request.metadata());
            metadata.validate();
        }
        Transaction updated = new Transaction(
                current.id(), current.externalId(), current.type(), current.currency(),
                request.occurredAt() != null ? request.occurredAt() : current.occurredAt(),
                total,
                request.taxLines() != null ? validTaxLines(request.taxLines()) : current.taxLines(),
                request.feeLines() != null ? validFeeLines(request.feeLines()) : current.feeLines(),
                metadata,
                current.createdAt(), Instant.now());
        repository.update(updated);
        return repository.findById(id).orElseThrow();
    }

    /** Deletes the transaction with all its lines and details. Refused with 409 if it is on any invoice. */
    @Transactional
    public void delete(String id) {
        if (repository.isOnAnyInvoice(id)) {
            throw new ConflictException("transaction " + id + " is on an invoice and cannot be deleted");
        }
        if (!repository.delete(id)) {
            throw new NotFoundException("transaction " + id + " not found");
        }
    }

    /** One page of transaction summaries. A single bounded query, so it needs no database transaction. */
    public Page<TransactionSummary> list(Integer limit, String cursor) {
        int pageSize = Pagination.validLimit(limit);
        return Pagination.toPage(repository.findPage(cursor, pageSize + 1), pageSize, TransactionSummary::id);
    }

    private Transaction buildValidTransaction(CreateTransactionRequest request, String externalId) {
        String typeValue = Validation.requireText(request.type(), "type");
        TransactionType type = TransactionType.fromValue(typeValue)
                .orElseThrow(() -> new ValidationException("type must be one of: flight, hotel, rail, navan_fee"));
        String currency = validCurrency(request.currency());
        Instant occurredAt = Validation.requirePresent(request.occurredAt(), "occurred_at");
        MoneyConverter.toCents(request.total(), "total");
        List<TaxLine> taxLines = validTaxLines(request.taxLines());
        List<FeeLine> feeLines = validFeeLines(request.feeLines());
        TransactionMetadata metadata = metadataParser.parse(type, request.metadata());
        metadata.validate();

        Instant now = Instant.now();
        return new Transaction(IdGenerator.newId("txn"), externalId, type, currency, occurredAt, request.total(),
                taxLines, feeLines, metadata, now, now);
    }

    private static String validCurrency(String currency) {
        Validation.requireText(currency, "currency");
        try {
            if (!currency.equals(currency.toUpperCase())) {
                throw new IllegalArgumentException();
            }
            Currency.getInstance(currency);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("currency must be an uppercase ISO 4217 code like EUR, got " + currency);
        }
        return currency;
    }

    private static List<TaxLine> validTaxLines(List<TaxLine> taxLines) {
        Validation.requirePresent(taxLines, "tax_lines");
        for (int i = 0; i < taxLines.size(); i++) {
            String field = "tax_lines[" + i + "]";
            Validation.requirePresent(taxLines.get(i), field).validate(field);
        }
        return taxLines;
    }

    private static List<FeeLine> validFeeLines(List<FeeLine> feeLines) {
        Validation.requirePresent(feeLines, "fee_lines");
        for (int i = 0; i < feeLines.size(); i++) {
            String field = "fee_lines[" + i + "]";
            Validation.requirePresent(feeLines.get(i), field).validate(field);
        }
        return feeLines;
    }

    private static ConflictException duplicate(String externalId, String existingId) {
        return new ConflictException("a transaction with external_id " + externalId + " already exists", existingId);
    }
}
