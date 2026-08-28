package com.vaibhav.ediflow.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CanonicalPayload(
    String poNumber,
    LocalDate poDate,
    String invoiceNumber,
    LocalDate invoiceDate,
    String referencedPoNumber,
    BigDecimal declaredTotal,
    BigDecimal totalAmount,
    List<CanonicalLine> lines
) { }
