package com.vaibhav.ediflow.order.service;

import com.vaibhav.ediflow.events.*;
import com.vaibhav.ediflow.order.domain.*;
import com.vaibhav.ediflow.order.repository.*;
import com.vaibhav.ediflow.order.rules.*;
import com.vaibhav.ediflow.order.statemachine.ProcessingStateMachine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

@Service
public class DocumentProcessor {
    private final PartnerRepository partners;
    private final EdiInterchangeRepository interchanges;
    private final DocumentProcessingStatusRepository documentStatuses;
    private final PurchaseOrderRepository orders;
    private final InvoiceRepository invoices;
    private final ProcessingStateMachine states;
    private final List<BusinessRule> rules;
    private final DeadLetterPublisher dlq;
    private final MeterRegistry metrics;
    private final Set<String> activeCodes;
    private final TransactionTemplate transactions;

    public DocumentProcessor(PartnerRepository partners, EdiInterchangeRepository interchanges,
        DocumentProcessingStatusRepository documentStatuses,
        PurchaseOrderRepository orders, InvoiceRepository invoices, ProcessingStateMachine states,
        List<BusinessRule> rules, DeadLetterPublisher dlq, MeterRegistry metrics,
        PlatformTransactionManager transactionManager,
        @Value("${ediflow.active-partner-codes:SENDERID}") String activeCodes) {
        this.partners = partners;
        this.interchanges = interchanges;
        this.documentStatuses = documentStatuses;
        this.orders = orders;
        this.invoices = invoices;
        this.states = states;
        this.rules = rules.stream().sorted(Comparator.comparingInt(BusinessRule::order)).toList();
        this.dlq = dlq;
        this.metrics = metrics;
        this.activeCodes = new HashSet<>(Arrays.asList(activeCodes.split(",")));
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public InterchangeStatus process(CanonicalEdiEvent event, String sourceTopic) {
        Timer.Sample sample = Timer.start(metrics);
        try {
            try {
                return Objects.requireNonNull(transactions.execute(
                    ignored -> processDocument(event, sourceTopic)));
            } catch (DataIntegrityViolationException duplicate) {
                if (!isInterchangeDuplicate(duplicate)) {
                    throw duplicate;
                }
                metrics.counter("ediflow.documents.duplicates",
                    "documentType", event.documentType().name()).increment();
                return Objects.requireNonNull(transactions.execute(ignored -> {
                    documentStatuses.save(new DocumentProcessingStatus(event, InterchangeStatus.DUPLICATE,
                        "Duplicate partner and ISA/ST control-number tuple."));
                    return InterchangeStatus.DUPLICATE;
                }));
            }
        } finally {
            sample.stop(Timer.builder("ediflow.processing.latency")
                .description("Time from Kafka consumption through business-rule persistence")
                .tag("documentType", event.documentType().name())
                .publishPercentileHistogram()
                .register(metrics));
        }
    }

    private InterchangeStatus processDocument(CanonicalEdiEvent event, String sourceTopic) {
        Partner partner = partners.findById(event.partnerId()).orElseGet(() -> partners.saveAndFlush(
            new Partner(event.partnerId(), event.partnerCode(), activeCodes.contains(event.partnerCode()) ? PartnerStatus.ACTIVE : PartnerStatus.PENDING)));
        EdiInterchange interchange = interchanges.saveAndFlush(new EdiInterchange(partner, event, InterchangeStatus.RECEIVED));
        if (event.documentType() == DocumentType.PO_850) processOrder(event, sourceTopic, partner, interchange);
        else processInvoice(event, sourceTopic, partner, interchange);
        documentStatuses.save(new DocumentProcessingStatus(event, interchange.getStatus(), null));
        return interchange.getStatus();
    }

    private void processOrder(CanonicalEdiEvent event, String topic, Partner partner, EdiInterchange interchange) {
        PurchaseOrder order = orders.save(new PurchaseOrder(interchange, partner, event.payload()));
        states.received(order);
        states.transition(order, OrderStatus.VALIDATING, "Business-rule evaluation started.");
        RuleResult result = evaluate(new RuleContext(event, partner, null));
        if (result.outcome() == RuleResult.Outcome.HOLD) return;
        if (result.outcome() == RuleResult.Outcome.REJECT) {
            states.transition(order, OrderStatus.REJECTED, result.reason());
            interchange.status(InterchangeStatus.REJECTED);
            dlq.publish(event, topic, result.reason());
            return;
        }
        states.transition(order, OrderStatus.VALIDATED, "All purchase-order rules passed.");
        states.transition(order, OrderStatus.PERSISTED, "Order persisted successfully.");
        interchange.status(InterchangeStatus.PROCESSED);
    }

    private void processInvoice(CanonicalEdiEvent event, String topic, Partner partner, EdiInterchange interchange) {
        PurchaseOrder referenced = orders.findFirstByPoNumberAndStatusOrderByCreatedAtDesc(
            event.payload().referencedPoNumber(), OrderStatus.PERSISTED).orElse(null);
        Invoice invoice = invoices.save(new Invoice(interchange, partner, event.payload()));
        states.received(invoice);
        states.transition(invoice, InvoiceStatus.VALIDATING, "Business-rule evaluation started.");
        RuleResult result = evaluate(new RuleContext(event, partner, referenced));
        if (result.outcome() == RuleResult.Outcome.HOLD) return;
        if (result.outcome() == RuleResult.Outcome.REJECT) {
            states.transition(invoice, InvoiceStatus.REJECTED, result.reason());
            interchange.status(InterchangeStatus.REJECTED);
            dlq.publish(event, topic, result.reason());
            return;
        }
        if (result.outcome() == RuleResult.Outcome.MATCH_EXCEPTION) {
            states.transition(invoice, InvoiceStatus.MATCH_EXCEPTION, result.reason());
            interchange.status(InterchangeStatus.PROCESSED);
            return;
        }
        states.transition(invoice, InvoiceStatus.VALIDATED, "Invoice and purchase-order totals match.");
        states.transition(invoice, InvoiceStatus.PERSISTED, "Invoice persisted successfully.");
        interchange.status(InterchangeStatus.PROCESSED);
    }

    private RuleResult evaluate(RuleContext context) {
        for (BusinessRule rule : rules) {
            RuleResult result = rule.evaluate(context);
            if (result.outcome() != RuleResult.Outcome.PASS) return result;
        }
        return RuleResult.pass();
    }

    private boolean isInterchangeDuplicate(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                String constraint = constraintViolation.getConstraintName();
                if (constraint != null
                        && constraint.toUpperCase(Locale.ROOT).contains("UQ_EDI_INTERCHANGE_CONTROL_NUMBERS")) {
                    return true;
                }
            }
        }
        return false;
    }
}
