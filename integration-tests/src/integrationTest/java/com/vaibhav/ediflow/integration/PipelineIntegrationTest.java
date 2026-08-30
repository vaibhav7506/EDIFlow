package com.vaibhav.ediflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaibhav.ediflow.order.domain.*;
import com.vaibhav.ediflow.order.repository.*;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = IntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("ediflow").withUsername("ediflow").withPassword("ediflow-test");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.2");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.key-deserializer", () -> "org.apache.kafka.common.serialization.StringDeserializer");
        registry.add("spring.kafka.consumer.value-deserializer", () -> "org.springframework.kafka.support.serializer.JsonDeserializer");
        registry.add("spring.kafka.consumer.properties.spring.json.trusted.packages", () -> "com.vaibhav.ediflow.events");
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        registry.add("ediflow.active-partner-codes", () -> "SENDERID");
        registry.add("ediflow.metrics.lag-poll-ms", () -> "600000");
    }

    @Autowired TestRestTemplate http;
    @Autowired PurchaseOrderRepository orders;
    @Autowired InvoiceRepository invoices;
    @Autowired EdiInterchangeRepository interchanges;
    @Autowired DeadLetterRepository deadLetters;
    @Autowired ObjectMapper objectMapper;

    @Test
    void criticalDocumentPathsUseRealKafkaAndPostgres() throws Exception {
        ResponseEntity<String> firstUpload = upload("valid-850.edi");
        assertEquals(HttpStatus.ACCEPTED, firstUpload.getStatusCode());
        UUID firstEventId = eventId(firstUpload);
        await(() -> orders.count() == 1);
        await(() -> status(firstEventId).getStatusCode() == HttpStatus.OK
                && Objects.requireNonNull(status(firstEventId).getBody()).contains("\"status\":\"PROCESSED\""));
        PurchaseOrder order = orders.findAll().getFirst();
        assertEquals(OrderStatus.PERSISTED, order.getStatus());
        assertEquals("1687.50", order.getTotalAmount().toPlainString());
        assertEquals(2, order.getLines().size());

        ResponseEntity<String> duplicateUpload = upload("duplicate-850.edi");
        assertEquals(HttpStatus.ACCEPTED, duplicateUpload.getStatusCode());
        UUID duplicateEventId = eventId(duplicateUpload);
        await(() -> status(duplicateEventId).getStatusCode() == HttpStatus.OK
                && Objects.requireNonNull(status(duplicateEventId).getBody()).contains("\"status\":\"DUPLICATE\""));
        assertEquals(1, interchanges.count());
        assertEquals(1, orders.count());

        ResponseEntity<String> lineCountMismatch = upload("line-count-mismatch-850.edi");
        assertEquals(HttpStatus.ACCEPTED, lineCountMismatch.getStatusCode());
        UUID lineCountMismatchEventId = eventId(lineCountMismatch);
        await(() -> status(lineCountMismatchEventId).getStatusCode() == HttpStatus.OK
                && Objects.requireNonNull(status(lineCountMismatchEventId).getBody()).contains("\"status\":\"REJECTED\""));
        assertTrue(orders.findAll().stream().anyMatch(orderWithMismatch ->
                orderWithMismatch.getStatus() == OrderStatus.REJECTED));

        ResponseEntity<String> malformed = upload("malformed-850.edi");
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, malformed.getStatusCode());
        await(() -> deadLetters.countByStatus(DeadLetterStatus.OPEN) == 2);
        ResponseEntity<String> deadLetterPage = http.getForEntity("/api/v1/dead-letters?size=10", String.class);
        assertEquals(HttpStatus.OK, deadLetterPage.getStatusCode());
        assertTrue(Objects.requireNonNull(deadLetterPage.getBody()).contains("segment count"));

        assertEquals(HttpStatus.ACCEPTED, upload("mismatched-810.edi").getStatusCode());
        await(() -> invoices.findAll().stream().anyMatch(i -> i.getStatus() == InvoiceStatus.MATCH_EXCEPTION));
        assertEquals(HttpStatus.ACCEPTED, upload("valid-810.edi").getStatusCode());
        await(() -> invoices.findAll().stream().anyMatch(i -> i.getStatus() == InvoiceStatus.PERSISTED));
    }

    private ResponseEntity<String> upload(String fixture) throws IOException {
        byte[] bytes = Objects.requireNonNull(getClass().getResourceAsStream("/fixtures/" + fixture)).readAllBytes();
        ByteArrayResource file = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fixture; }
        };
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", file);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Partner-Id", "SENDERID");
        return http.exchange("/api/v1/edi/upload", HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
    }

    private UUID eventId(ResponseEntity<String> response) throws IOException {
        return UUID.fromString(objectMapper.readTree(Objects.requireNonNull(response.getBody())).required("eventId").asText());
    }

    private ResponseEntity<String> status(UUID eventId) {
        return http.getForEntity("/api/v1/edi/status/" + eventId, String.class);
    }

    private void await(java.util.concurrent.Callable<Boolean> condition) {
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(condition);
    }
}
