package com.vaibhav.ediflow.ingestion.producer;

import com.vaibhav.ediflow.events.*;
import com.vaibhav.ediflow.ingestion.config.KafkaTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.UUID;

@Component
public class EdiPublisher {
    private final KafkaTemplate<String, Object> kafka;
    public EdiPublisher(KafkaTemplate<String, Object> kafka) { this.kafka = kafka; }
    public void publish(CanonicalEdiEvent event) {
        String topic = event.documentType() == DocumentType.PO_850 ? KafkaTopics.PO : KafkaTopics.INVOICE;
        kafka.send(topic, event.partitionKey(), event);
    }
    public void deadLetter(String rawPayload, String error) {
        var event = new DeadLetterEvent(UUID.randomUUID(), "edi-upload", null, null, rawPayload, null, error, KafkaTopics.INGESTION_RETRY, Instant.now());
        kafka.send(KafkaTopics.DLQ, event.eventId().toString(), event);
    }
}
