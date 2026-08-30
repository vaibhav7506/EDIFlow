package com.vaibhav.ediflow.ingestion.controller;

import com.vaibhav.ediflow.ingestion.parser.EdiParseException;
import com.vaibhav.ediflow.ingestion.service.IngestionService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/edi")
public class UploadController {
    private final IngestionService ingestion;
    public UploadController(IngestionService ingestion) { this.ingestion = ingestion; }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UploadResponse> upload(@RequestPart("file") MultipartFile file, @RequestHeader("X-Partner-Id") String authenticatedPartner) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Upload an EDI file with content.");
        var event = ingestion.ingest(file.getBytes(), authenticatedPartner);
        return ResponseEntity.accepted().body(new UploadResponse(event.eventId(), event.documentType().name(), "QUEUED"));
    }
    record UploadResponse(UUID eventId, String documentType, String status) { }
    record ErrorResponse(Instant timestamp, int status, String error) { }
    @ExceptionHandler(EdiParseException.class) ResponseEntity<ErrorResponse> parse(EdiParseException e) { return ResponseEntity.unprocessableEntity().body(new ErrorResponse(Instant.now(), 422, e.getMessage())); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ErrorResponse> bad(IllegalArgumentException e) { return ResponseEntity.badRequest().body(new ErrorResponse(Instant.now(), 400, e.getMessage())); }
}
