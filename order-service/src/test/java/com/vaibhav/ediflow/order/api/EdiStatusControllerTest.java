package com.vaibhav.ediflow.order.api;

import com.vaibhav.ediflow.events.CanonicalEdiEvent;
import com.vaibhav.ediflow.events.DocumentType;
import com.vaibhav.ediflow.order.domain.DocumentProcessingStatus;
import com.vaibhav.ediflow.order.domain.InterchangeStatus;
import com.vaibhav.ediflow.order.repository.DocumentProcessingStatusRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EdiStatusController.class)
class EdiStatusControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DocumentProcessingStatusRepository statuses;

    @Test
    void returnsTerminalStatusForAcceptedEventId() throws Exception {
        UUID eventId = UUID.randomUUID();
        CanonicalEdiEvent event = new CanonicalEdiEvent(eventId, UUID.randomUUID(), "SENDERID",
                DocumentType.PO_850, "ISA1", "GS1", "ST1", Instant.now(), null);
        when(statuses.findById(eventId)).thenReturn(Optional.of(
                new DocumentProcessingStatus(event, InterchangeStatus.PROCESSED, null)));

        mvc.perform(get("/api/v1/edi/status/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.documentType").value("PO_850"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    void returnsNotFoundUntilEventHasBeenConsumed() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(statuses.findById(eventId)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/edi/status/{eventId}", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("EDI event status not found."));
    }
}
