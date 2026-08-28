package com.vaibhav.ediflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(optional = false, cascade = CascadeType.PERSIST) @JoinColumn(name = "interchange_id", unique = true) private EdiInterchange interchange;
    @ManyToOne(optional = false) @JoinColumn(name = "partner_id") private Partner partner;
    @Column(name = "po_number", nullable = false) private String poNumber;
    @Column(name = "po_date") private LocalDate poDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OrderStatus status;
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2) private BigDecimal totalAmount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER) @OrderBy("lineNumber") private List<PurchaseOrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() { }
    public PurchaseOrder(EdiInterchange interchange, Partner partner, String poNumber, LocalDate poDate, BigDecimal total) {
        this.interchange = interchange; this.partner = partner; this.poNumber = poNumber; this.poDate = poDate;
        this.totalAmount = total; this.status = OrderStatus.PERSISTED; this.createdAt = Instant.now();
    }
    public void addLine(PurchaseOrderLine line) { line.setPurchaseOrder(this); lines.add(line); }
    public UUID getId() { return id; }
    public Partner getPartner() { return partner; }
    public String getPoNumber() { return poNumber; }
    public LocalDate getPoDate() { return poDate; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<PurchaseOrderLine> getLines() { return List.copyOf(lines); }
}
