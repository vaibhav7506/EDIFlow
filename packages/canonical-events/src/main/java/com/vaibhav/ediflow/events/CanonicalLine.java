package com.vaibhav.ediflow.events;

import java.math.BigDecimal;

public record CanonicalLine(int lineNumber, String sku, BigDecimal quantity, String unitOfMeasure, BigDecimal unitPrice) {
    public BigDecimal extendedPrice() { return quantity.multiply(unitPrice); }
}
