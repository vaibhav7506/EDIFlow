package com.vaibhav.ediflow.order.domain;

import jakarta.persistence.*;
import java.math.*;
import java.util.UUID;

@Entity @Table(name="purchase_order_lines")
public class PurchaseOrderLine {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(optional=false) @JoinColumn(name="purchase_order_id") PurchaseOrder purchaseOrder;
    @Column(name="line_number",nullable=false) private int lineNumber;
    @Column(nullable=false) private String sku;
    @Column(nullable=false,precision=19,scale=4) private BigDecimal quantity;
    @Column(name="unit_of_measure",nullable=false) private String unitOfMeasure;
    @Column(name="unit_price",nullable=false,precision=19,scale=4) private BigDecimal unitPrice;
    @Column(name="extended_price",nullable=false,precision=19,scale=2) private BigDecimal extendedPrice;
    protected PurchaseOrderLine(){} PurchaseOrderLine(int n,String sku,BigDecimal q,String u,BigDecimal p){lineNumber=n;this.sku=sku;quantity=q;unitOfMeasure=u;unitPrice=p;extendedPrice=q.multiply(p).setScale(2,RoundingMode.HALF_UP);}
    public int getLineNumber(){return lineNumber;} public String getSku(){return sku;} public BigDecimal getQuantity(){return quantity;} public String getUnitOfMeasure(){return unitOfMeasure;} public BigDecimal getUnitPrice(){return unitPrice;} public BigDecimal getExtendedPrice(){return extendedPrice;}
}
