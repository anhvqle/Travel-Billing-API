package com.travelbilling.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.travelbilling.shared.Page;
import com.travelbilling.transaction.TransactionService;
import com.travelbilling.transaction.model.Transaction;
import com.travelbilling.transaction.model.TransactionSummary;
import com.travelbilling.transaction.request.CreateTransactionRequest;
import com.travelbilling.transaction.request.UpdateTransactionRequest;

/** HTTP endpoints for transactions. Only translates HTTP to service calls; all rules live in TransactionService. */
@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction create(@RequestBody CreateTransactionRequest request) {
        return transactionService.create(request);
    }

    @GetMapping
    public Page<TransactionSummary> list(@RequestParam(required = false) Integer limit,
                                         @RequestParam(required = false) String cursor) {
        return transactionService.list(limit, cursor);
    }

    @GetMapping("/{id}")
    public Transaction get(@PathVariable String id) {
        return transactionService.get(id);
    }

    @PatchMapping("/{id}")
    public Transaction update(@PathVariable String id, @RequestBody UpdateTransactionRequest request) {
        return transactionService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        transactionService.delete(id);
    }
}
