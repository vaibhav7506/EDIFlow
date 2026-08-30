package com.vaibhav.ediflow.ingestion.consumer;

import com.vaibhav.ediflow.events.RawEdiRetryEvent;
import com.vaibhav.ediflow.ingestion.config.KafkaTopics;
import com.vaibhav.ediflow.ingestion.parser.EdiParseException;
import com.vaibhav.ediflow.ingestion.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class RetryConsumer {
    private static final Logger log = LoggerFactory.getLogger(RetryConsumer.class);
    private final IngestionService ingestion;
    public RetryConsumer(IngestionService ingestion) { this.ingestion = ingestion; }
    @KafkaListener(topics = KafkaTopics.INGESTION_RETRY, groupId = "ediflow-ingestion-retry")
    void retry(RawEdiRetryEvent event) {
        try {
            ingestion.ingest(event.rawPayload().getBytes(StandardCharsets.UTF_8));
        } catch (EdiParseException exception) {
            // IngestionService has already published this failed attempt to the DLQ.
            // Acknowledge the retry record so Kafka does not amplify one poison
            // message into the listener container's full automatic retry sequence.
            log.warn("Retry event {} remains malformed and was returned to the DLQ: {}",
                event.eventId(), exception.getMessage());
        }
    }
}
