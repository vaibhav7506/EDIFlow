package com.vaibhav.ediflow.order.domain;

import com.vaibhav.ediflow.events.CanonicalPayload;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name="purchase_orders")
public class PurchaseOrder {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(optional=false) @JoinColumn(name="interchange_id",unique=true) private EdiInterchange interchange;
    @ManyToOne(optional=false) @JoinColumn(name="partner_id") private Partner partner;
    @Column(name="po_number",nullable=false) private String poNumber;
    @Column(name="po_date") private LocalDate poDate;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private OrderStatus status;
    @Column(name="total_amount",nullable=false,precision=19,scale=2) private BigDecimal totalAmount;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @OneToMany(mappedBy="purchaseOrder",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.EAGER) @OrderBy("lineNumber") private List<PurchaseOrderLine> lines=new ArrayList<>();
    protected PurchaseOrder() { }
    public PurchaseOrder(EdiInterchange interchange,Partner partner,CanonicalPayload p){this.interchange=interchange;this.partner=partner;poNumber=p.poNumber();poDate=p.poDate();status=OrderStatus.RECEIVED;totalAmount=p.totalAmount();createdAt=Instant.now();p.lines().forEach(l->add(new PurchaseOrderLine(l.lineNumber(),l.sku(),l.quantity(),l.unitOfMeasure(),l.unitPrice())));}
    private void add(PurchaseOrderLine l){l.purchaseOrder=this;lines.add(l);} public void status(OrderStatus s){status=s;} public UUID getId(){return id;} public Partner getPartner(){return partner;} public String getPoNumber(){return poNumber;} public LocalDate getPoDate(){return poDate;} public OrderStatus getStatus(){return status;} public BigDecimal getTotalAmount(){return totalAmount;} public Instant getCreatedAt(){return createdAt;} public List<PurchaseOrderLine> getLines(){return List.copyOf(lines);}
}
