package com.vaibhav.ediflow.order.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="invoice_lines") public class InvoiceLine {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id; @ManyToOne(optional=false) @JoinColumn(name="invoice_id") Invoice invoice; @Column(name="line_number",nullable=false) private int lineNumber; @Column(nullable=false) private String sku; @Column(nullable=false,precision=19,scale=4) private BigDecimal quantity; @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal unitPrice;
 protected InvoiceLine(){} InvoiceLine(int n,String sku,BigDecimal q,BigDecimal p){lineNumber=n;this.sku=sku;quantity=q;unitPrice=p;} public int getLineNumber(){return lineNumber;} public String getSku(){return sku;} public BigDecimal getQuantity(){return quantity;} public BigDecimal getUnitPrice(){return unitPrice;}
}
