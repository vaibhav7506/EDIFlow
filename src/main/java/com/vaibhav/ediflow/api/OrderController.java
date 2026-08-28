package com.vaibhav.ediflow.api;

import com.vaibhav.ediflow.domain.OrderStatus;
import com.vaibhav.ediflow.service.OrderService;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    @GetMapping
    public Page<ApiModels.OrderSummary> list(@RequestParam(required = false) UUID partnerId, @RequestParam(required = false) OrderStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return orders.list(partnerId, status, from, to, pageable).map(ApiModels.OrderSummary::from);
    }
    @GetMapping("/{id}") public ApiModels.OrderDetail get(@PathVariable UUID id) { return ApiModels.OrderDetail.from(orders.get(id)); }
}
