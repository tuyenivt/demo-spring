package com.example.idempotent.controller;

import com.example.idempotent.dto.OrderItem;
import com.example.idempotent.dto.OrderRequest;
import com.example.idempotent.dto.OrderResponse;
import com.example.idempotent.idempotent.Idempotent;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Demo controller showcasing @Idempotent annotation for order creation.
 * Prevents duplicate order creation from accidental form resubmission.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo/orders")
public class OrderDemoController {

    @PostMapping
    @Idempotent
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("Creating order with {} items", request.getItems() != null ? request.getItems().size() : 0);

        var response = OrderResponse.builder()
                .orderId(UUID.randomUUID().toString())
                .items(request.getItems())
                .total(calculateTotal(request.getItems()))
                .status("CREATED")
                .createdAt(Instant.now())
                .build();

        log.info("Order created successfully: orderId={}", response.getOrderId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Demo endpoint showing DELETE operation with @Idempotent.
     * Deleting the same resource twice should return success without error.
     * Cached duplicate responses preserve the original 204 No Content status.
     */
    @DeleteMapping("/{orderId}")
    @Idempotent
    public ResponseEntity<Void> cancelOrder(@PathVariable String orderId) {
        log.info("Cancelling order: {}", orderId);

        // Simulate order cancellation
        // In a real system, this would check if the order exists and cancel it
        // Subsequent calls with the same idempotent key should return the cached success response

        log.info("Order cancelled successfully: orderId={}", orderId);
        return ResponseEntity.noContent().build();
    }

    private BigDecimal calculateTotal(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
