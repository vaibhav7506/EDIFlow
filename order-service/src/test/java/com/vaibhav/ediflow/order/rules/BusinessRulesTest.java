package com.vaibhav.ediflow.order.rules;

import com.vaibhav.ediflow.events.*;
import com.vaibhav.ediflow.order.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessRulesTest {
    @Test void r1PartnerStatusPassesHoldsAndRejects() {
        var rule = new PartnerStatusRule();
        assertEquals(RuleResult.Outcome.PASS, rule.evaluate(context(PartnerStatus.ACTIVE, orderPayload(null))).outcome());
        assertEquals(RuleResult.Outcome.HOLD, rule.evaluate(context(PartnerStatus.PENDING, orderPayload(null))).outcome());
        assertEquals(RuleResult.Outcome.REJECT, rule.evaluate(context(PartnerStatus.SUSPENDED, orderPayload(null))).outcome());
    }

    @Test void r2LineCountPassesValidCttAndRejectsMismatchWithStableCode() {
        var rule = new OrderLineCountRule();
        assertEquals(RuleResult.Outcome.PASS,
            rule.evaluate(context(PartnerStatus.ACTIVE, orderPayload(1))).outcome());
        RuleResult mismatch = rule.evaluate(context(PartnerStatus.ACTIVE, orderPayload(2)));
        assertEquals(RuleResult.Outcome.REJECT, mismatch.outcome());
        assertTrue(mismatch.reason().startsWith("LINE_COUNT_MISMATCH:"));
    }

    @Test void r4LineValuesRejectZeroQuantityAndNegativePrice() {
        var rule = new LineValueRule();
        assertEquals(RuleResult.Outcome.REJECT, rule.evaluate(context(PartnerStatus.ACTIVE,
            payloadWithLine(BigDecimal.ZERO, BigDecimal.ONE))).outcome());
        assertEquals(RuleResult.Outcome.REJECT, rule.evaluate(context(PartnerStatus.ACTIVE,
            payloadWithLine(BigDecimal.ONE, new BigDecimal("-0.01")))).outcome());
        assertEquals(RuleResult.Outcome.PASS, rule.evaluate(context(PartnerStatus.ACTIVE,
            payloadWithLine(BigDecimal.ONE, BigDecimal.ZERO))).outcome());
    }

    @Test void r5InvoiceMatchUsesToleranceAndSurfacesMissingOrMismatchedOrders() {
        var rule = new InvoiceMatchRule();
        Partner partner = partner(PartnerStatus.ACTIVE);
        CanonicalPayload orderPayload = orderPayload(1);
        CanonicalEdiEvent orderEvent = event(partner, DocumentType.PO_850, orderPayload);
        PurchaseOrder order = new PurchaseOrder(new EdiInterchange(partner, orderEvent, InterchangeStatus.RECEIVED), partner, orderPayload);
        assertEquals(RuleResult.Outcome.PASS, rule.evaluate(new RuleContext(
            event(partner, DocumentType.INVOICE_810, invoicePayload(new BigDecimal("10.01"))), partner, order)).outcome());
        assertEquals(RuleResult.Outcome.MATCH_EXCEPTION, rule.evaluate(new RuleContext(
            event(partner, DocumentType.INVOICE_810, invoicePayload(new BigDecimal("10.02"))), partner, order)).outcome());
        assertEquals(RuleResult.Outcome.MATCH_EXCEPTION, rule.evaluate(new RuleContext(
            event(partner, DocumentType.INVOICE_810, invoicePayload(new BigDecimal("10.00"))), partner, null)).outcome());
    }

    private RuleContext context(PartnerStatus status, CanonicalPayload payload) {
        Partner partner = partner(status);
        return new RuleContext(event(partner, DocumentType.PO_850, payload), partner, null);
    }
    private Partner partner(PartnerStatus status) { return new Partner(UUID.randomUUID(), "PARTNER", status); }
    private CanonicalPayload orderPayload(Integer declaredLineCount) { return new CanonicalPayload("PO-1", LocalDate.now(), null, null, null, declaredLineCount, new BigDecimal("10.00"), List.of(new CanonicalLine(1,"SKU",BigDecimal.ONE,"EA",new BigDecimal("10.00")))); }
    private CanonicalPayload payloadWithLine(BigDecimal quantity, BigDecimal price) { return new CanonicalPayload("PO-1", LocalDate.now(), null, null, null, null, quantity.multiply(price), List.of(new CanonicalLine(1,"SKU",quantity,"EA",price))); }
    private CanonicalPayload invoicePayload(BigDecimal total) { return new CanonicalPayload(null,null,"INV-1",LocalDate.now(),"PO-1",null,total,List.of(new CanonicalLine(1,"SKU",BigDecimal.ONE,"EA",total))); }
    private CanonicalEdiEvent event(Partner partner, DocumentType type, CanonicalPayload payload) { return new CanonicalEdiEvent(UUID.randomUUID(),partner.getId(),partner.getCode(),type,"ISA1","GS1","ST1",Instant.now(),payload); }
}
