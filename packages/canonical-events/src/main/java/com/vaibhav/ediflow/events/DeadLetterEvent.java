package com.vaibhav.ediflow.events;

import java.time.Instant;
import java.util.UUID;

public record DeadLetterEvent(
    UUID eventId,
    String sourceTopic,
    UUID partnerId,
    String partnerCode,
    String rawPayload,
    CanonicalEdiEvent canonicalEvent,
    String errorReason,
    String retryTopic,
    Instant createdAt
) { }
