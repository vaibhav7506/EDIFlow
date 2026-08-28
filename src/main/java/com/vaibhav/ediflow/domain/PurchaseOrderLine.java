package com.vaibhav.ediflow.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_lines")
public class PurchaseOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) @JoinColumn(name = "purchase_order_id") private PurchaseOrder purchaseOrder;
    @Column(name = "line_number", nullable = false) private Integer lineNumber;
    @Column(nullable = false) private String sku;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal quantity;
    @Column(name = "unit_of_measure", nullable = false) private String unitOfMeasure;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;
    @Column(name = "extended_price", nullable = false, precision = 19, scale = 2) private BigDecimal extendedPrice;
    protected PurchaseOrderLine() { }
    public PurchaseOrderLine(Integer number, String sku, BigDecimal qty, String uom, BigDecimal price) {
        lineNumber = number; this.sku = sku; quantity = qty; unitOfMeasure = uom; unitPrice = price;
        extendedPrice = qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    void setPurchaseOrder(PurchaseOrder purchaseOrder) { this.purchaseOrder = purchaseOrder; }
    public Integer getLineNumber() { return lineNumber; }
    public String getSku() { return sku; }
    public BigDecimal getQuantity() { return quantity; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getExtendedPrice() { return extendedPrice; }
}
