package com.vaibhav.ediflow.events;

import java.time.Instant;
import java.util.UUID;

public record CanonicalEdiEvent(
    UUID eventId,
    UUID partnerId,
    String partnerCode,
    DocumentType documentType,
    String isaControlNumber,
    String gsControlNumber,
    String stControlNumber,
    Instant receivedAt,
    CanonicalPayload payload
) {
    public String partitionKey() { return partnerId + ":" + isaControlNumber; }
}
