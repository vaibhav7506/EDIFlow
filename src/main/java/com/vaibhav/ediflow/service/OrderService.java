package com.vaibhav.ediflow.service;

import com.vaibhav.ediflow.domain.*;
import com.vaibhav.ediflow.parser.ParsedPurchaseOrder;
import com.vaibhav.ediflow.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
public class OrderService {
    private final PartnerRepository partners;
    private final PurchaseOrderRepository orders;
    public OrderService(PartnerRepository partners, PurchaseOrderRepository orders) { this.partners = partners; this.orders = orders; }

    @Transactional
    public PurchaseOrder persist(ParsedPurchaseOrder parsed) {
        Partner partner = partners.findByCode(parsed.senderId()).orElseGet(() -> partners.save(new Partner(parsed.senderId())));
        EdiInterchange interchange = new EdiInterchange(partner, parsed.isaControlNumber(), parsed.gsControlNumber(), parsed.stControlNumber());
        PurchaseOrder order = new PurchaseOrder(interchange, partner, parsed.poNumber(), parsed.poDate(), parsed.totalAmount());
        parsed.lines().forEach(line -> order.addLine(new PurchaseOrderLine(line.lineNumber(), line.sku(), line.quantity(), line.unitOfMeasure(), line.unitPrice())));
        return orders.save(order);
    }
    @Transactional(readOnly = true)
    public Page<PurchaseOrder> list(UUID partnerId, OrderStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        Specification<PurchaseOrder> spec = Specification.where(null);
        if (partnerId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("partner").get("id"), partnerId));
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        if (from != null) spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("poDate"), from));
        if (to != null) spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("poDate"), to));
        return orders.findAll(spec, pageable);
    }
    @Transactional(readOnly = true)
    public PurchaseOrder get(UUID id) { return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id)); }
    public static class OrderNotFoundException extends RuntimeException { public OrderNotFoundException(UUID id) { super("Order " + id + " was not found."); } }
}
