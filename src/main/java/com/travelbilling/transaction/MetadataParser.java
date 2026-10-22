package com.travelbilling.transaction;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelbilling.shared.ValidationException;
import com.travelbilling.transaction.metadata.FeeKind;
import com.travelbilling.transaction.metadata.FlightMetadata;
import com.travelbilling.transaction.metadata.HotelMetadata;
import com.travelbilling.transaction.metadata.NavanFeeMetadata;
import com.travelbilling.transaction.metadata.RailMetadata;
import com.travelbilling.transaction.metadata.TransactionMetadata;
import com.travelbilling.transaction.model.TransactionType;

/** Turns the raw "metadata" JSON into the record for the transaction's type (flight, hotel, rail, navan_fee). */
@Component
public class MetadataParser {

    private final ObjectMapper objectMapper;

    public MetadataParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Reads the metadata for the given type. Required fields are checked afterwards by validate(). */
    public TransactionMetadata parse(TransactionType type, JsonNode metadata) {
        if (metadata == null || !metadata.isObject()) {
            throw new ValidationException("metadata is required and must be a JSON object");
        }
        if (type == TransactionType.NAVAN_FEE) {
            JsonNode feeKind = metadata.get("fee_kind");
            if (feeKind != null && !feeKind.isNull() && FeeKind.fromValue(feeKind.asText()).isEmpty()) {
                throw new ValidationException("metadata.fee_kind must be one of: trip_fee, agent_call_fee");
            }
        }
        Class<? extends TransactionMetadata> metadataClass = switch (type) {
            case FLIGHT -> FlightMetadata.class;
            case HOTEL -> HotelMetadata.class;
            case RAIL -> RailMetadata.class;
            case NAVAN_FEE -> NavanFeeMetadata.class;
        };
        try {
            return objectMapper.treeToValue(metadata, metadataClass);
        } catch (JsonProcessingException e) {
            throw new ValidationException("metadata is not valid for type " + type.value() + ": " + e.getOriginalMessage());
        }
    }
}
