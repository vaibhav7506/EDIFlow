package com.vaibhav.ediflow.order.api;

import com.vaibhav.ediflow.events.DocumentType;
import com.vaibhav.ediflow.order.domain.DocumentProcessingStatus;
import com.vaibhav.ediflow.order.domain.InterchangeStatus;
import com.vaibhav.ediflow.order.repository.DocumentProcessingStatusRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/edi/status")
public class EdiStatusController {
    private final DocumentProcessingStatusRepository statuses;

    public EdiStatusController(DocumentProcessingStatusRepository statuses) {
        this.statuses = statuses;
    }

    @GetMapping("/{eventId}")
    StatusResponse get(@PathVariable UUID eventId) {
        return statuses.findById(eventId)
                .map(StatusResponse::from)
                .orElseThrow(() -> new NotFound("EDI event status not found."));
    }

    record StatusResponse(
            UUID eventId,
            DocumentType documentType,
            InterchangeStatus status,
            String detail,
            Instant updatedAt) {
        static StatusResponse from(DocumentProcessingStatus status) {
            return new StatusResponse(status.getEventId(), status.getDocumentType(), status.getStatus(),
                    status.getDetail(), status.getUpdatedAt());
        }
    }
}
