package com.example.idempotent.controller;

import com.example.idempotent.dto.PaymentRequest;
import com.example.idempotent.dto.PaymentResponse;
import com.example.idempotent.idempotent.Idempotent;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Demo controller showcasing @Idempotent annotation for payment processing.
 * Prevents duplicate payment charges when network issues cause retries.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo/payments")
public class PaymentDemoController {

    @PostMapping
    @Idempotent
    public ResponseEntity<PaymentResponse> processPayment(@Valid @RequestBody PaymentRequest request) {
        log.info("Processing payment: amount={}, currency={}", request.getAmount(), request.getCurrency());

        // Simulate payment processing
        var response = PaymentResponse.builder()
                .transactionId(UUID.randomUUID().toString())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("COMPLETED")
                .processedAt(Instant.now())
                .build();

        log.info("Payment processed successfully: transactionId={}", response.getTransactionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Demonstrates per-endpoint idempotent configuration override.
     */
    @PostMapping("/refunds")
    @Idempotent(timeout = 30, timeUnit = TimeUnit.SECONDS, resultExpire = 60)
    public ResponseEntity<PaymentResponse> processRefund(@Valid @RequestBody PaymentRequest request) {
        log.info("Processing refund: amount={}, currency={}", request.getAmount(), request.getCurrency());

        var response = PaymentResponse.builder()
                .transactionId(UUID.randomUUID().toString())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("REFUNDED")
                .processedAt(Instant.now())
                .build();

        log.info("Refund processed successfully: refundId={}", response.getTransactionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Slow endpoint used by integration tests to validate "in-progress" duplicate handling.
     */
    @PostMapping("/slow")
    @Idempotent
    public ResponseEntity<PaymentResponse> processSlowPayment(@Valid @RequestBody PaymentRequest request) throws InterruptedException {
        log.info("Processing slow payment: amount={}, currency={}", request.getAmount(), request.getCurrency());
        Thread.sleep(1500);

        var response = PaymentResponse.builder()
                .transactionId(UUID.randomUUID().toString())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("COMPLETED")
                .processedAt(Instant.now())
                .build();

        log.info("Slow payment processed successfully: transactionId={}", response.getTransactionId());
        return ResponseEntity.ok(response);
    }
}
