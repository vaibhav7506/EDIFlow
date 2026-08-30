package com.vaibhav.ediflow.order.service;

import com.vaibhav.ediflow.events.CanonicalEdiEvent;
import com.vaibhav.ediflow.events.CanonicalLine;
import com.vaibhav.ediflow.events.CanonicalPayload;
import com.vaibhav.ediflow.events.DocumentType;
import com.vaibhav.ediflow.order.domain.InterchangeStatus;
import com.vaibhav.ediflow.order.domain.InvoiceStatus;
import com.vaibhav.ediflow.order.domain.OrderStatus;
import com.vaibhav.ediflow.order.domain.Partner;
import com.vaibhav.ediflow.order.domain.PartnerStatus;
import com.vaibhav.ediflow.order.repository.DocumentProcessingStatusRepository;
import com.vaibhav.ediflow.order.repository.EdiInterchangeRepository;
import com.vaibhav.ediflow.order.repository.InvoiceRepository;
import com.vaibhav.ediflow.order.repository.PartnerRepository;
import com.vaibhav.ediflow.order.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DocumentProcessorTest {
    @Autowired DocumentProcessor processor;
    @Autowired PurchaseOrderRepository orders;
    @Autowired InvoiceRepository invoices;
    @Autowired EdiInterchangeRepository interchanges;
    @Autowired PartnerRepository partners;
    @Autowired DocumentProcessingStatusRepository documentStatuses;
    @MockBean DeadLetterPublisher dlq;

    @BeforeEach
    void clearPersistedDocuments() {
        documentStatuses.deleteAll();
        invoices.deleteAll();
        orders.deleteAll();
        interchanges.deleteAll();
        partners.deleteAll();
    }

    @Test
    void validOrderPersistsAndDuplicateIsRecordedWithoutSecondOrder() {
        var event = order("SENDERID", "000000905", "0001");

        InterchangeStatus first = processor.process(event, "ediflow.po.parsed.v1");
        InterchangeStatus duplicate = processor.process(event, "ediflow.po.parsed.v1");

        assertEquals(1, orders.count());
        assertEquals(1, interchanges.count());
        assertEquals(OrderStatus.PERSISTED, orders.findAll().getFirst().getStatus());
        assertEquals(InterchangeStatus.PROCESSED, first);
        assertEquals(InterchangeStatus.DUPLICATE, duplicate);
    }

    @Test
    void concurrentDuplicateAttemptsPersistExactlyOneInterchangeAndOrder() throws Exception {
        var event = order("SENDERID", "000000920", "0020");
        partners.saveAndFlush(new Partner(event.partnerId(), event.partnerCode(), PartnerStatus.ACTIVE));
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<InterchangeStatus> first = executor.submit(() -> processAfterStart(event, ready, start));
            Future<InterchangeStatus> second = executor.submit(() -> processAfterStart(event, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            var outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(InterchangeStatus.PROCESSED::equals).count());
            assertEquals(1, outcomes.stream().filter(InterchangeStatus.DUPLICATE::equals).count());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, interchanges.count());
        assertEquals(1, orders.count());
    }

    @Test
    void matchingInvoicePersistsAndMismatchBecomesException() {
        processor.process(order("SENDERID", "000000905", "0001"), "ediflow.po.parsed.v1");
        processor.process(invoice("000000906", "0002", new BigDecimal("1687.50")), "ediflow.invoice.parsed.v1");
        processor.process(invoice("000000907", "0003", new BigDecimal("1800.00")), "ediflow.invoice.parsed.v1");

        assertEquals(2, invoices.count());
        assertTrue(invoices.findAll().stream().anyMatch(i -> i.getStatus() == InvoiceStatus.PERSISTED));
        assertTrue(invoices.findAll().stream().anyMatch(i -> i.getStatus() == InvoiceStatus.MATCH_EXCEPTION));
    }

    @Test
    void unknownPartnerIsHeldInValidating() {
        processor.process(order("NEWPARTNER", "000000910", "0010"), "ediflow.po.parsed.v1");

        assertEquals(OrderStatus.VALIDATING, orders.findAll().getFirst().getStatus());
        assertEquals(PartnerStatus.PENDING, partners.findByCode("NEWPARTNER").orElseThrow().getStatus());
    }

    private InterchangeStatus processAfterStart(CanonicalEdiEvent event, CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent duplicate test");
            }
            return processor.process(event, "ediflow.po.parsed.v1");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private CanonicalEdiEvent order(String code, String isa, String st) {
        var lines = List.of(
                new CanonicalLine(1, "SKU-4471", new BigDecimal("100"), "EA", new BigDecimal("12.50")),
                new CanonicalLine(2, "SKU-9981", new BigDecimal("50"), "EA", new BigDecimal("8.75")));
        return event(code, DocumentType.PO_850, isa, st,
                new CanonicalPayload("PO-100245", LocalDate.of(2026, 8, 28), null, null, null, null,
                        new BigDecimal("1687.50"), lines));
    }

    private CanonicalEdiEvent invoice(String isa, String st, BigDecimal total) {
        var lines = List.of(new CanonicalLine(1, "SKU-4471", BigDecimal.ONE, "EA", total));
        return event("SENDERID", DocumentType.INVOICE_810, isa, st,
                new CanonicalPayload(null, null, "INV-" + st, LocalDate.of(2026, 8, 29), "PO-100245", null,
                        total, lines));
    }

    private CanonicalEdiEvent event(String code, DocumentType type, String isa, String st, CanonicalPayload payload) {
        UUID partner = UUID.nameUUIDFromBytes(("ediflow-partner:" + code).getBytes(StandardCharsets.UTF_8));
        return new CanonicalEdiEvent(UUID.randomUUID(), partner, code, type, isa, isa, st, Instant.now(), payload);
    }
}
