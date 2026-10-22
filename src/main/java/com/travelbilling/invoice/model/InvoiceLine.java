package com.travelbilling.invoice.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.JsonNode;
import com.travelbilling.transaction.model.TransactionType;

/**
 * One transaction on an invoice, frozen at the moment it was invoiced.
 * snapshot is a full copy of the transaction (metadata, tax_lines, fee_lines) as it was then;
 * later changes to the transaction never change it.
 */
public record InvoiceLine(String transactionId, TransactionType type, BigDecimal total, JsonNode snapshot) {
}
