package com.vaibhav.ediflow.parser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public record ParsedPurchaseOrder(String senderId, String isaControlNumber, String gsControlNumber, String stControlNumber, String poNumber, LocalDate poDate, List<ParsedLine> lines, BigDecimal totalAmount) {
    public record ParsedLine(int lineNumber, String sku, BigDecimal quantity, String unitOfMeasure, BigDecimal unitPrice) { }
}
