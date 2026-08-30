package com.vaibhav.ediflow.ingestion.service;

import com.vaibhav.ediflow.events.CanonicalEdiEvent;
import com.vaibhav.ediflow.ingestion.parser.*;
import com.vaibhav.ediflow.ingestion.producer.EdiPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class IngestionService {
    private final X12Parser parser; private final EdiPublisher publisher; private final MeterRegistry metrics;
    public IngestionService(X12Parser parser, EdiPublisher publisher, MeterRegistry metrics) { this.parser = parser; this.publisher = publisher; this.metrics = metrics; }
    public CanonicalEdiEvent ingest(byte[] bytes) { return ingest(bytes, null); }
    public CanonicalEdiEvent ingest(byte[] bytes, String authenticatedPartnerCode) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        try {
            CanonicalEdiEvent parsed = parser.parse(new ByteArrayInputStream(bytes));
            CanonicalEdiEvent event = authenticatedPartnerCode == null ? parsed : authenticated(parsed, authenticatedPartnerCode);
            publisher.publish(event);
            metrics.counter("ediflow.documents.ingested", "documentType", event.documentType().name()).increment();
            return event;
        }
        catch (EdiParseException e) { metrics.counter("ediflow.parse.errors").increment(); publisher.deadLetter(raw, e.getMessage()); throw e; }
    }
    private CanonicalEdiEvent authenticated(CanonicalEdiEvent parsed, String partnerCode) {
        UUID partnerId = UUID.nameUUIDFromBytes(("ediflow-partner:" + partnerCode).getBytes(StandardCharsets.UTF_8));
        return new CanonicalEdiEvent(parsed.eventId(), partnerId, partnerCode, parsed.documentType(), parsed.isaControlNumber(), parsed.gsControlNumber(), parsed.stControlNumber(), parsed.receivedAt(), parsed.payload());
    }
}
