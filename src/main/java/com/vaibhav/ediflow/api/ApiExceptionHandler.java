package com.vaibhav.ediflow.api;

import com.vaibhav.ediflow.parser.EdiParseException;
import com.vaibhav.ediflow.service.OrderService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EdiParseException.class) ResponseEntity<ApiModels.ErrorResponse> parse(EdiParseException e) { return response(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ApiModels.ErrorResponse> badRequest(IllegalArgumentException e) { return response(HttpStatus.BAD_REQUEST, e.getMessage()); }
    @ExceptionHandler(OrderService.OrderNotFoundException.class) ResponseEntity<ApiModels.ErrorResponse> missing(OrderService.OrderNotFoundException e) { return response(HttpStatus.NOT_FOUND, e.getMessage()); }
    private ResponseEntity<ApiModels.ErrorResponse> response(HttpStatus status, String message) { return ResponseEntity.status(status).body(new ApiModels.ErrorResponse(Instant.now(), status.value(), message)); }
}
