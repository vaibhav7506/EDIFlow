package com.vaibhav.ediflow.events;

import java.util.UUID;

public record RawEdiRetryEvent(UUID eventId, String rawPayload) { }
