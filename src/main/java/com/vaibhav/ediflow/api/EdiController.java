package com.vaibhav.ediflow.api;

import com.vaibhav.ediflow.domain.OrderStatus;
import com.vaibhav.ediflow.parser.X12PurchaseOrderParser;
import com.vaibhav.ediflow.service.OrderService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/edi")
public class EdiController {
    private final X12PurchaseOrderParser parser; private final OrderService orders;
    public EdiController(X12PurchaseOrderParser parser, OrderService orders) { this.parser = parser; this.orders = orders; }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiModels.UploadResponse> upload(@RequestPart("file") @NotNull MultipartFile file) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Upload an EDI file with content.");
        var order = orders.persist(parser.parse(file.getInputStream()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiModels.UploadResponse(order.getId(), order.getStatus().name()));
    }
}
