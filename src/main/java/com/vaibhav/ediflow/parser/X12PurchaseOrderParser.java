package com.vaibhav.ediflow.parser;

import io.xlate.edi.stream.EDIInputFactory;
import io.xlate.edi.stream.EDIStreamEvent;
import io.xlate.edi.stream.EDIStreamReader;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class X12PurchaseOrderParser {
    public ParsedPurchaseOrder parse(InputStream input) {
        Map<String, List<List<String>>> segments = new LinkedHashMap<>();
        List<String> current = null;
        String currentName = null;
        try (EDIStreamReader reader = EDIInputFactory.newFactory().createEDIStreamReader(input)) {
            while (reader.hasNext()) {
                EDIStreamEvent event = reader.next();
                if (event == EDIStreamEvent.START_SEGMENT) { currentName = reader.getText(); current = new ArrayList<>(); }
                else if (event == EDIStreamEvent.ELEMENT_DATA && current != null) { current.add(reader.getText()); }
                else if (event == EDIStreamEvent.END_SEGMENT && currentName != null) { segments.computeIfAbsent(currentName, ignored -> new ArrayList<>()).add(current); current = null; currentName = null; }
            }
        } catch (Exception e) { throw new EdiParseException("Invalid X12 document: " + concise(e), e); }
        return map(segments);
    }

    private ParsedPurchaseOrder map(Map<String, List<List<String>>> s) {
        List<String> isa = first(s, "ISA"); List<String> gs = first(s, "GS"); List<String> st = first(s, "ST"); List<String> beg = first(s, "BEG"); List<String> se = first(s, "SE");
        if (!"850".equals(value(st, 0))) throw new EdiParseException("Only X12 850 purchase orders are supported in Phase 1.");
        String sender = required(value(isa, 5), "ISA06 sender ID");
        String po = required(value(beg, 2), "BEG03 purchase order number");
        validateSegmentCount(s, se);
        List<ParsedPurchaseOrder.ParsedLine> lines = new ArrayList<>();
        for (List<String> po1 : s.getOrDefault("PO1", List.of())) {
            BigDecimal quantity = decimal(value(po1, 1), "PO1 quantity");
            BigDecimal price = decimal(value(po1, 3), "PO1 unit price");
            String sku = productCode(po1);
            lines.add(new ParsedPurchaseOrder.ParsedLine(integer(value(po1, 0), "PO1 line number"), required(sku, "PO1 product code"), quantity, required(value(po1, 2), "PO1 UOM"), price));
        }
        if (lines.isEmpty()) throw new EdiParseException("850 must contain at least one PO1 line item.");
        BigDecimal total = lines.stream().map(l -> l.quantity().multiply(l.unitPrice())).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ParsedPurchaseOrder(sender.trim(), required(value(isa, 12), "ISA13"), required(value(gs, 5), "GS06"), required(value(st, 1), "ST02"), po, date(value(beg, 4)), List.copyOf(lines), total);
    }
    private void validateSegmentCount(Map<String, List<List<String>>> s, List<String> se) {
        int declared = integer(value(se, 0), "SE01 segment count"); int actual = 2;
        boolean withinTransaction = false;
        for (Map.Entry<String, List<List<String>>> entry : s.entrySet()) {
            if ("ST".equals(entry.getKey())) withinTransaction = true;
            if (withinTransaction) actual += entry.getValue().size();
            if ("SE".equals(entry.getKey())) break;
        }
        // The map contains ST and SE already; actual starts at zero for clarity.
        actual = s.entrySet().stream().filter(e -> !List.of("ISA", "GS", "GE", "IEA").contains(e.getKey())).mapToInt(e -> e.getValue().size()).sum();
        if (declared != actual) throw new EdiParseException("SE01 segment count is " + declared + " but transaction contains " + actual + " segments.");
    }
    private static List<String> first(Map<String, List<List<String>>> s, String name) { List<List<String>> values = s.get(name); if (values == null || values.isEmpty()) throw new EdiParseException("Missing required " + name + " segment."); return values.getFirst(); }
    private static String value(List<String> values, int index) { return index < values.size() ? values.get(index) : null; }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new EdiParseException("Missing " + name + "."); return value.trim(); }
    private static BigDecimal decimal(String value, String name) { try { return new BigDecimal(required(value, name)); } catch (NumberFormatException e) { throw new EdiParseException("Invalid " + name + "."); } }
    private static int integer(String value, String name) { try { return Integer.parseInt(required(value, name)); } catch (NumberFormatException e) { throw new EdiParseException("Invalid " + name + "."); } }
    private static LocalDate date(String value) { try { return LocalDate.parse(required(value, "BEG05 date"), DateTimeFormatter.BASIC_ISO_DATE); } catch (Exception e) { throw new EdiParseException("Invalid BEG05 date."); } }
    private static String productCode(List<String> po1) { for (int i = 5; i + 1 < po1.size(); i += 2) if ("VP".equals(value(po1, i)) || "BP".equals(value(po1, i))) return value(po1, i + 1); return null; }
    private static String concise(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
