package com.vaibhav.ediflow.order.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class KafkaLagMetrics {
    private static final String GROUP_ID = "ediflow-order-service";
    private final Admin admin;
    private final AtomicLong lag = new AtomicLong();

    public KafkaLagMetrics(MeterRegistry registry,
        @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        Gauge.builder("ediflow.kafka.consumer.lag", lag, AtomicLong::get)
            .description("Total unconsumed records for the order-service consumer group")
            .register(registry);
    }

    @Scheduled(initialDelayString = "${ediflow.metrics.lag-poll-ms:15000}",
        fixedDelayString = "${ediflow.metrics.lag-poll-ms:15000}")
    public void refresh() {
        try {
            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(GROUP_ID)
                .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);
            if (committed.isEmpty()) { lag.set(0); return; }
            Map<TopicPartition, OffsetSpec> latestRequest = committed.keySet().stream()
                .collect(Collectors.toMap(partition -> partition, ignored -> OffsetSpec.latest()));
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = admin.listOffsets(latestRequest)
                .all().get(5, TimeUnit.SECONDS);
            long total = committed.entrySet().stream().mapToLong(entry ->
                Math.max(0, latest.get(entry.getKey()).offset() - entry.getValue().offset())).sum();
            lag.set(total);
        } catch (Exception ignored) {
            // A transient broker outage should not make the application unhealthy; retain the last observation.
        }
    }

    @PreDestroy
    void close() { admin.close(Duration.ofSeconds(2)); }
}
