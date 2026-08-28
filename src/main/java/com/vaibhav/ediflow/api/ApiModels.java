package com.vaibhav.ediflow.api;

import com.vaibhav.ediflow.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ApiModels {
    private ApiModels() { }
    public record UploadResponse(UUID orderId, String status) { }
    public record ErrorResponse(Instant timestamp, int status, String error) { }
    public record OrderSummary(UUID id, UUID partnerId, String partnerCode, String poNumber, LocalDate poDate, OrderStatus status, BigDecimal totalAmount, Instant createdAt) {
        public static OrderSummary from(PurchaseOrder o) { return new OrderSummary(o.getId(), o.getPartner().getId(), o.getPartner().getCode(), o.getPoNumber(), o.getPoDate(), o.getStatus(), o.getTotalAmount(), o.getCreatedAt()); }
    }
    public record OrderDetail(UUID id, UUID partnerId, String partnerCode, String poNumber, LocalDate poDate, OrderStatus status, BigDecimal totalAmount, List<Line> lines) {
        public static OrderDetail from(PurchaseOrder o) { return new OrderDetail(o.getId(), o.getPartner().getId(), o.getPartner().getCode(), o.getPoNumber(), o.getPoDate(), o.getStatus(), o.getTotalAmount(), o.getLines().stream().map(Line::from).toList()); }
    }
    public record Line(Integer lineNumber, String sku, BigDecimal quantity, String unitOfMeasure, BigDecimal unitPrice, BigDecimal extendedPrice) {
        static Line from(PurchaseOrderLine l) { return new Line(l.getLineNumber(), l.getSku(), l.getQuantity(), l.getUnitOfMeasure(), l.getUnitPrice(), l.getExtendedPrice()); }
    }
}
