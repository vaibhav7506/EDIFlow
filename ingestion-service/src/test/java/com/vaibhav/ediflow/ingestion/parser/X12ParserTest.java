package com.vaibhav.ediflow.ingestion.parser;

import com.vaibhav.ediflow.events.DocumentType;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class X12ParserTest {
    private final X12Parser parser=new X12Parser();
    @Test void parses850WithDynamicIsaDelimiters()throws Exception{var e=parse("valid-850.edi");assertEquals(DocumentType.PO_850,e.documentType());assertEquals("PO-100245",e.payload().poNumber());assertEquals(new BigDecimal("1687.50"),e.payload().totalAmount());assertEquals(2,e.payload().lines().size());}
    @Test void parses810AndConvertsTdsCents()throws Exception{var e=parse("valid-810.edi");assertEquals(DocumentType.INVOICE_810,e.documentType());assertEquals("INV-2001",e.payload().invoiceNumber());assertEquals("PO-100245",e.payload().referencedPoNumber());assertEquals(new BigDecimal("1687.50"),e.payload().totalAmount());}
    @Test void mapsBegPurchaseOrderFields()throws Exception{var p=parse("valid-850.edi").payload();assertEquals("PO-100245",p.poNumber());assertEquals(java.time.LocalDate.of(2026,8,28),p.poDate());}
    @Test void mapsPo1LineFieldsAndVendorProductCode()throws Exception{var line=parse("valid-850.edi").payload().lines().getFirst();assertEquals(1,line.lineNumber());assertEquals("SKU-4471",line.sku());assertEquals(new BigDecimal("100"),line.quantity());assertEquals("EA",line.unitOfMeasure());assertEquals(new BigDecimal("12.50"),line.unitPrice());assertEquals(new BigDecimal("1250.00"),line.extendedPrice());}
    @Test void mapsBigInvoiceReferences()throws Exception{var p=parse("valid-810.edi").payload();assertEquals("INV-2001",p.invoiceNumber());assertEquals(java.time.LocalDate.of(2026,8,29),p.invoiceDate());assertEquals("PO-100245",p.referencedPoNumber());}
    @Test void mapsIt1InvoiceLine()throws Exception{var line=parse("valid-810.edi").payload().lines().get(1);assertEquals(2,line.lineNumber());assertEquals("SKU-9981",line.sku());assertEquals(new BigDecimal("50"),line.quantity());assertEquals(new BigDecimal("8.75"),line.unitPrice());}
    @Test void mapsCtt01AsLineCount()throws Exception{assertEquals(2,parse("valid-850.edi").payload().declaredLineCount());}
    @Test void exposesMismatchedCtt01ForR2()throws Exception{var payload=parse("line-count-mismatch-850.edi").payload();assertEquals(3,payload.declaredLineCount());assertEquals(2,payload.lines().size());}
    @Test void ignoresCtt02InsteadOfTreatingItAsMoney()throws Exception{String edi=Files.readString(path("valid-850.edi")).replace("CTT*2~","CTT*2*999999~");var event=parser.parse(new ByteArrayInputStream(edi.getBytes(StandardCharsets.UTF_8)));assertEquals(2,event.payload().declaredLineCount());assertEquals(new BigDecimal("1687.50"),event.payload().totalAmount());}
    @Test void mapsTdsFromImpliedCents()throws Exception{assertEquals(new BigDecimal("1687.50"),parse("valid-810.edi").payload().totalAmount());}
    @Test void mapsEnvelopeControlNumbers()throws Exception{var e=parse("valid-850.edi");assertEquals("000000905",e.isaControlNumber());assertEquals("905",e.gsControlNumber());assertEquals("0001",e.stControlNumber());}
    @Test void rejectsBadSegmentCount()throws Exception{try(var in=Files.newInputStream(path("malformed-850.edi"))){var error=assertThrows(EdiParseException.class,()->parser.parse(in));assertTrue(error.getMessage().contains("segment count"));}}
    private com.vaibhav.ediflow.events.CanonicalEdiEvent parse(String name)throws Exception{try(var in=Files.newInputStream(path(name))){return parser.parse(in);}}
    private Path path(String name){return Path.of("src/test/resources/fixtures",name);}
}
