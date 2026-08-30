package com.vaibhav.ediflow.ingestion.controller;

import com.vaibhav.ediflow.events.CanonicalEdiEvent;
import com.vaibhav.ediflow.events.DocumentType;
import com.vaibhav.ediflow.ingestion.service.IngestionService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
class UploadControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean IngestionService ingestion;

    @Test
    void returnsAcceptedWithStableEventIdForPolling() throws Exception {
        UUID eventId = UUID.randomUUID();
        CanonicalEdiEvent event = new CanonicalEdiEvent(eventId, UUID.randomUUID(), "SENDERID",
                DocumentType.PO_850, "ISA1", "GS1", "ST1", Instant.now(), null);
        when(ingestion.ingest(any(byte[].class), eq("SENDERID"))).thenReturn(event);
        var file = new MockMultipartFile("file", "order.edi", "text/plain", "ISA*00*".getBytes());

        mvc.perform(multipart("/api/v1/edi/upload")
                        .file(file)
                        .header("X-Partner-Id", "SENDERID"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.documentType").value("PO_850"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void rejectsEmptyUploadBeforePublishing() throws Exception {
        var file = new MockMultipartFile("file", "empty.edi", "text/plain", new byte[0]);

        mvc.perform(multipart("/api/v1/edi/upload")
                        .file(file)
                        .header("X-Partner-Id", "SENDERID"))
                .andExpect(status().isBadRequest());
    }
}
