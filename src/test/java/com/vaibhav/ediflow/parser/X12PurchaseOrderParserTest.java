package com.vaibhav.ediflow.parser;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class X12PurchaseOrderParserTest {
    private final X12PurchaseOrderParser parser = new X12PurchaseOrderParser();
    @Test void parsesAValid850AndCalculatesTotal() throws Exception {
        var parsed = parser.parse(Files.newInputStream(Path.of("src/test/resources/fixtures/valid-850.edi")));
        assertEquals("SENDERID", parsed.senderId()); assertEquals("PO-100245", parsed.poNumber());
        assertEquals(2, parsed.lines().size()); assertEquals(new BigDecimal("1687.50"), parsed.totalAmount());
        assertEquals("SKU-4471", parsed.lines().getFirst().sku());
    }
    @Test void rejectsIncorrectTransactionSegmentCount() throws Exception {
        var input = Files.newInputStream(Path.of("src/test/resources/fixtures/malformed-850.edi"));
        var error = assertThrows(EdiParseException.class, () -> parser.parse(input));
        assertTrue(error.getMessage().contains("segment count"));
    }
}
