package com.vaibhav.ediflow.ingestion.parser;

import com.vaibhav.ediflow.events.*;
import io.xlate.edi.stream.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class X12Parser {
    public CanonicalEdiEvent parse(InputStream input) {
        Map<String, List<List<String>>> segments = read(input);
        List<String> isa = first(segments, "ISA");
        List<String> gs = first(segments, "GS");
        List<String> st = first(segments, "ST");
        List<String> se = first(segments, "SE");
        validateSegmentCount(segments, se);
        String code = required(value(isa, 5), "ISA06 sender ID").trim();
        DocumentType type = switch (required(value(st, 0), "ST01 document type")) {
            case "850" -> DocumentType.PO_850;
            case "810" -> DocumentType.INVOICE_810;
            default -> throw new EdiParseException("Only X12 850 and 810 documents are supported.");
        };
        CanonicalPayload payload = type == DocumentType.PO_850 ? purchaseOrder(segments) : invoice(segments);
        UUID partnerId = UUID.nameUUIDFromBytes(("ediflow-partner:" + code).getBytes(StandardCharsets.UTF_8));
        return new CanonicalEdiEvent(UUID.randomUUID(), partnerId, code, type,
            required(value(isa, 12), "ISA13"), required(value(gs, 5), "GS06"), required(value(st, 1), "ST02"), Instant.now(), payload);
    }

    private Map<String, List<List<String>>> read(InputStream input) {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        List<String> current = null; String name = null;
        try (EDIStreamReader reader = EDIInputFactory.newFactory().createEDIStreamReader(input)) {
            while (reader.hasNext()) {
                EDIStreamEvent event = reader.next();
                if (event == EDIStreamEvent.START_SEGMENT) { name = reader.getText(); current = new ArrayList<>(); }
                else if (event == EDIStreamEvent.ELEMENT_DATA && current != null) current.add(reader.getText());
                else if (event == EDIStreamEvent.END_SEGMENT && name != null) { result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(current); name = null; current = null; }
            }
            return result;
        } catch (Exception e) { throw new EdiParseException("Invalid X12 document: " + concise(e), e); }
    }

    private CanonicalPayload purchaseOrder(Map<String, List<List<String>>> segments) {
        List<String> beg = first(segments, "BEG");
        List<CanonicalLine> lines = lines(segments.getOrDefault("PO1", List.of()), "PO1");
        BigDecimal calculated = total(lines);
        Integer declaredLineCount = optionalInteger(value(optionalFirst(segments, "CTT"), 0), "CTT01 line count");
        return new CanonicalPayload(required(value(beg, 2), "BEG03 PO number"), date(value(beg, 4), "BEG05 PO date"), null, null, null, declaredLineCount, calculated, lines);
    }

    private CanonicalPayload invoice(Map<String, List<List<String>>> segments) {
        List<String> big = first(segments, "BIG");
        List<CanonicalLine> lines = lines(segments.getOrDefault("IT1", List.of()), "IT1");
        BigDecimal tds = moneyInCents(value(first(segments, "TDS"), 0));
        return new CanonicalPayload(null, null, required(value(big, 1), "BIG02 invoice number"), date(value(big, 0), "BIG01 invoice date"),
            required(value(big, 3), "BIG04 referenced PO number"), null, tds, lines);
    }

    private List<CanonicalLine> lines(List<List<String>> rawLines, String segment) {
        if (rawLines.isEmpty()) throw new EdiParseException(segment + " line item is required.");
        List<CanonicalLine> lines = new ArrayList<>();
        for (List<String> line : rawLines) {
            int number = integer(value(line, 0), segment + " line number");
            BigDecimal quantity = decimal(value(line, 1), segment + " quantity");
            String uom = required(value(line, 2), segment + " UOM");
            BigDecimal price = decimal(value(line, 3), segment + " unit price");
            lines.add(new CanonicalLine(number, required(productCode(line), segment + " product code"), quantity, uom, price));
        }
        return List.copyOf(lines);
    }

    private void validateSegmentCount(Map<String, List<List<String>>> s, List<String> se) {
        int declared = integer(value(se, 0), "SE01 segment count");
        int actual = s.entrySet().stream().filter(e -> !List.of("ISA", "GS", "GE", "IEA").contains(e.getKey())).mapToInt(e -> e.getValue().size()).sum();
        if (declared != actual) throw new EdiParseException("SE01 segment count is " + declared + " but transaction contains " + actual + " segments.");
        String stControl = required(value(first(s, "ST"), 1), "ST02");
        if (!stControl.equals(required(value(se, 1), "SE02"))) throw new EdiParseException("SE02 control number does not match ST02.");
    }

    private static BigDecimal total(List<CanonicalLine> lines) { return lines.stream().map(CanonicalLine::extendedPrice).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP); }
    private static String productCode(List<String> line) { for (int i = 5; i + 1 < line.size(); i += 2) if (Set.of("VP", "BP", "SK").contains(value(line, i))) return value(line, i + 1); return null; }
    private static List<String> first(Map<String, List<List<String>>> s, String name) { List<List<String>> values = s.get(name); if (values == null || values.isEmpty()) throw new EdiParseException("Missing required " + name + " segment."); return values.getFirst(); }
    private static List<String> optionalFirst(Map<String, List<List<String>>> s, String name) { List<List<String>> values = s.get(name); return values == null || values.isEmpty() ? List.of() : values.getFirst(); }
    private static String value(List<String> values, int index) { return index >= 0 && index < values.size() ? values.get(index) : null; }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new EdiParseException("Missing " + name + "."); return value.trim(); }
    private static BigDecimal decimal(String value, String name) { try { return new BigDecimal(required(value, name)); } catch (NumberFormatException e) { throw new EdiParseException("Invalid " + name + "."); } }
    private static Integer optionalInteger(String value, String name) { return value == null || value.isBlank() ? null : integer(value, name); }
    private static BigDecimal moneyInCents(String value) { return decimal(value, "TDS01 total").movePointLeft(2).setScale(2, RoundingMode.HALF_UP); }
    private static int integer(String value, String name) { try { return Integer.parseInt(required(value, name)); } catch (NumberFormatException e) { throw new EdiParseException("Invalid " + name + "."); } }
    private static LocalDate date(String value, String name) { try { return LocalDate.parse(required(value, name), DateTimeFormatter.BASIC_ISO_DATE); } catch (Exception e) { throw new EdiParseException("Invalid " + name + "."); } }
    private static String concise(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
