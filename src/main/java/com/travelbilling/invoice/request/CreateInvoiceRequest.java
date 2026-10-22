package com.travelbilling.invoice.request;

import java.util.List;

/**
 * The body of POST /invoices (and PATCH /invoices/{id}). Send either one id or a list:
 * {"transaction_id": "txn_1"} or {"transaction_ids": ["txn_1", "txn_2"]}.
 */
public record CreateInvoiceRequest(String transactionId, List<String> transactionIds) {
}
