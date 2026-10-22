package com.travelbilling.transaction.metadata;

import com.travelbilling.shared.Validation;
import com.travelbilling.transaction.model.TransactionType;

/**
 * A Navan-owned charge, e.g. a trip fee or an agent call fee.
 * related_external_id optionally names the booking the fee belongs to.
 */
public record NavanFeeMetadata(FeeKind feeKind, String description, String relatedExternalId)
        implements TransactionMetadata {

    @Override
    public TransactionType type() {
        return TransactionType.NAVAN_FEE;
    }

    @Override
    public void validate() {
        Validation.requirePresent(feeKind, "metadata.fee_kind");
    }
}
