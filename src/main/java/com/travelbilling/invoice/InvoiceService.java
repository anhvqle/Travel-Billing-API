package com.travelbilling.invoice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelbilling.invoice.model.Invoice;
import com.travelbilling.invoice.model.InvoiceLine;
import com.travelbilling.invoice.request.CreateInvoiceRequest;
import com.travelbilling.shared.ConflictException;
import com.travelbilling.shared.IdGenerator;
import com.travelbilling.shared.NotFoundException;
import com.travelbilling.shared.Validation;
import com.travelbilling.shared.ValidationException;
import com.travelbilling.transaction.TransactionService;
import com.travelbilling.transaction.model.Transaction;

/**
 * Business rules for invoices. An invoice is a snapshot: the transactions are copied in when the invoice
 * is created, so later changes to a transaction never change an invoice that already billed it.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository repository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public InvoiceService(InvoiceRepository repository, TransactionService transactionService,
                          ObjectMapper objectMapper) {
        this.repository = repository;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    /** Creates an invoice from one or more transaction ids, in one database transaction. */
    @Transactional
    public Invoice create(CreateInvoiceRequest request) {
        Instant now = Instant.now();
        Invoice invoice = buildSnapshot(IdGenerator.newId("inv"), requestedIds(request), now, now);
        repository.insert(invoice);
        return get(invoice.id());
    }

    @Transactional
    public Invoice get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("invoice " + id + " not found"));
    }

    /** Loads the transactions, checks they share one currency, copies them into lines and adds them up. */
    private Invoice buildSnapshot(String id, List<String> transactionIds, Instant createdAt, Instant updatedAt) {
        List<Transaction> transactions = new ArrayList<>();
        for (String transactionId : transactionIds) {
            transactions.add(transactionService.get(transactionId));
        }
        String currency = transactions.get(0).currency();
        for (Transaction transaction : transactions) {
            if (!transaction.currency().equals(currency)) {
                throw new ConflictException("all transactions on an invoice must have the same currency, found "
                        + currency + " and " + transaction.currency());
            }
        }
        List<InvoiceLine> lines = transactions.stream()
                .map(t -> new InvoiceLine(t.id(), t.type(), t.total(), objectMapper.valueToTree(t)))
                .toList();
        InvoiceCalculator.Rollups rollups = InvoiceCalculator.calculate(transactions);
        return new Invoice(id, currency, lines, rollups.taxRollup(), rollups.feeRollup(),
                InvoiceCalculator.totalsByKind(lines), createdAt, updatedAt);
    }

    /** Either transaction_id or transaction_ids, not both; no blanks; the same id may not appear twice. */
    private static List<String> requestedIds(CreateInvoiceRequest request) {
        boolean hasOne = request.transactionId() != null;
        boolean hasList = request.transactionIds() != null;
        if (hasOne == hasList) {
            throw new ValidationException("send either transaction_id or transaction_ids");
        }
        List<String> ids = hasOne ? List.of(request.transactionId()) : request.transactionIds();
        if (ids.isEmpty()) {
            throw new ValidationException("transaction_ids must not be empty");
        }
        for (int i = 0; i < ids.size(); i++) {
            Validation.requireText(ids.get(i), "transaction_ids[" + i + "]");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ConflictException("the same transaction id is listed more than once");
        }
        return ids;
    }
}
