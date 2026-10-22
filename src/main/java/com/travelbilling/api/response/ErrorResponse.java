package com.travelbilling.api.response;

/**
 * The JSON body of every error reply, e.g.
 * {"error": "conflict", "message": "a transaction with external_id ... already exists", "existing_id": "txn_..."}.
 * existing_id is only present for conflicts with a stored record.
 */
public record ErrorResponse(String error, String message, String existingId) {
}
