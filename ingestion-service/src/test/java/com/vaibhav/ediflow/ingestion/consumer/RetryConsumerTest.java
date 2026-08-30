package com.vaibhav.ediflow.ingestion.consumer;

import com.vaibhav.ediflow.events.RawEdiRetryEvent;
import com.vaibhav.ediflow.ingestion.parser.EdiParseException;
import com.vaibhav.ediflow.ingestion.service.IngestionService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RetryConsumerTest {
    @Test
    void acknowledgesMalformedRetryAfterIngestionPublishesItToTheDlq() {
        IngestionService ingestion = mock(IngestionService.class);
        byte[] payload = "malformed edi".getBytes(StandardCharsets.UTF_8);
        doThrow(new EdiParseException("bad envelope")).when(ingestion).ingest(payload);
        RetryConsumer consumer = new RetryConsumer(ingestion);

        assertDoesNotThrow(() -> consumer.retry(new RawEdiRetryEvent(UUID.randomUUID(), "malformed edi")));

        verify(ingestion, times(1)).ingest(payload);
    }
}
