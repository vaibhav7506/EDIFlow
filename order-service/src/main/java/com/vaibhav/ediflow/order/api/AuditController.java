package com.vaibhav.ediflow.order.api;

import com.vaibhav.ediflow.order.domain.ProcessingEvent;
import com.vaibhav.ediflow.order.repository.ProcessingEventRepository;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final ProcessingEventRepository events;
    public AuditController(ProcessingEventRepository events) { this.events = events; }

    @GetMapping
    Page<View> search(@RequestParam(defaultValue = "") String query,
        @PageableDefault(size = 50, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return events.search(query.trim(), pageable).map(View::from);
    }

    record View(UUID id, String entityType, UUID entityId, String fromState, String toState,
        String reason, Instant occurredAt) {
        static View from(ProcessingEvent event) {
            return new View(event.getId(), event.getEntityType(), event.getEntityId(), event.getFromState(),
                event.getToState(), event.getReason(), event.getOccurredAt());
        }
    }
}
