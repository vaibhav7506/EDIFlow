package com.vaibhav.ediflow.order.domain;

import com.vaibhav.ediflow.events.CanonicalPayload;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name="invoices")
public class Invoice {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(optional=false) @JoinColumn(name="interchange_id",unique=true) private EdiInterchange interchange;
    @ManyToOne(optional=false) @JoinColumn(name="partner_id") private Partner partner;
    @Column(name="invoice_number",nullable=false) private String invoiceNumber;
    @Column(name="invoice_date",nullable=false) private LocalDate invoiceDate;
    @Column(name="referenced_po_number",nullable=false) private String referencedPoNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false) private InvoiceStatus status;
    @Column(name="total_amount",nullable=false,precision=19,scale=2) private BigDecimal totalAmount;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @OneToMany(mappedBy="invoice",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.EAGER) @OrderBy("lineNumber") private List<InvoiceLine> lines=new ArrayList<>();
    protected Invoice(){} public Invoice(EdiInterchange i,Partner partner,CanonicalPayload p){interchange=i;this.partner=partner;invoiceNumber=p.invoiceNumber();invoiceDate=p.invoiceDate();referencedPoNumber=p.referencedPoNumber();status=InvoiceStatus.RECEIVED;totalAmount=p.totalAmount();createdAt=Instant.now();p.lines().forEach(l->add(new InvoiceLine(l.lineNumber(),l.sku(),l.quantity(),l.unitPrice())));}
    private void add(InvoiceLine l){l.invoice=this;lines.add(l);} public void status(InvoiceStatus s){status=s;} public UUID getId(){return id;} public Partner getPartner(){return partner;} public String getInvoiceNumber(){return invoiceNumber;} public LocalDate getInvoiceDate(){return invoiceDate;} public String getReferencedPoNumber(){return referencedPoNumber;} public InvoiceStatus getStatus(){return status;} public BigDecimal getTotalAmount(){return totalAmount;} public Instant getCreatedAt(){return createdAt;} public List<InvoiceLine> getLines(){return List.copyOf(lines);}
}
