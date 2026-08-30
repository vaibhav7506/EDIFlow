package com.vaibhav.ediflow.order.domain;

import com.vaibhav.ediflow.events.CanonicalEdiEvent;
import com.vaibhav.ediflow.events.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_processing_statuses")
public class DocumentProcessingStatus {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterchangeStatus status;

    @Column(length = 1000)
    private String detail;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentProcessingStatus() { }

    public DocumentProcessingStatus(CanonicalEdiEvent event, InterchangeStatus status, String detail) {
        this.eventId = event.eventId();
        this.documentType = event.documentType();
        this.status = status;
        this.detail = detail;
        this.updatedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public DocumentType getDocumentType() { return documentType; }
    public InterchangeStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public Instant getUpdatedAt() { return updatedAt; }
}
