package com.vaibhav.ediflow.ingestion.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {
    public static final String PO = "ediflow.po.parsed.v1";
    public static final String INVOICE = "ediflow.invoice.parsed.v1";
    public static final String DLQ = "ediflow.dlq.v1";
    public static final String INGESTION_RETRY = "ediflow.ingestion.retry.v1";
    @Bean NewTopic poTopic() { return topic(PO); }
    @Bean NewTopic invoiceTopic() { return topic(INVOICE); }
    @Bean NewTopic dlqTopic() { return topic(DLQ); }
    @Bean NewTopic retryTopic() { return topic(INGESTION_RETRY); }
    private NewTopic topic(String name) { return TopicBuilder.name(name).partitions(3).replicas(1).build(); }
}
