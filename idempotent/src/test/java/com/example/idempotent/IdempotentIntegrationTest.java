package com.example.idempotent;

import com.example.idempotent.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldReturnCachedResponseOnDuplicatePaymentRequest() {
        var idempotentKey = UUID.randomUUID().toString();
        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new PaymentRequest(new BigDecimal("100.00"), "USD", "Test payment");
        var entity = new HttpEntity<>(request, headers);

        // First request - processes payment
        var first = restTemplate.postForEntity("/api/demo/payments", entity, PaymentResponse.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(first.getBody().getTransactionId());

        // Duplicate request - should return same response (cached)
        var second = restTemplate.postForEntity("/api/demo/payments", entity, PaymentResponse.class);
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertNotNull(second.getBody());
        assertEquals(first.getBody().getTransactionId(), second.getBody().getTransactionId());
    }

    @Test
    void shouldReturnCachedResponseOnDuplicateOrderRequest() {
        var idempotentKey = UUID.randomUUID().toString();
        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var items = List.of(new OrderItem("PROD-001", "Test Product", 2, new BigDecimal("50.00")));
        var request = new OrderRequest(items, "123 Main St");
        var entity = new HttpEntity<>(request, headers);

        // First request - creates order
        var first = restTemplate.postForEntity("/api/demo/orders", entity, OrderResponse.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertNotNull(first.getBody());
        assertNotNull(first.getBody().getOrderId());
        assertEquals(new BigDecimal("100.00"), first.getBody().getTotal());

        // Duplicate request - should return same response (cached)
        var second = restTemplate.postForEntity("/api/demo/orders", entity, OrderResponse.class);
        assertEquals(HttpStatus.CREATED, second.getStatusCode());
        assertNotNull(second.getBody());
        assertEquals(first.getBody().getOrderId(), second.getBody().getOrderId());
    }

    @Test
    void shouldPreserveNoContentStatusOnDuplicateOrderCancelRequest() {
        var idempotentKey = UUID.randomUUID().toString();
        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);

        var entity = new HttpEntity<>(headers);

        var first = restTemplate.exchange("/api/demo/orders/order-42", org.springframework.http.HttpMethod.DELETE, entity, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, first.getStatusCode());

        var second = restTemplate.exchange("/api/demo/orders/order-42", org.springframework.http.HttpMethod.DELETE, entity, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, second.getStatusCode());
    }

    @Test
    void shouldReturn409WhenSubscriptionDuplicated() {
        var idempotentKey = UUID.randomUUID().toString();
        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new SubscribeRequest("test@example.com", "Test User");
        var entity = new HttpEntity<>(request, headers);

        // First request - subscribes
        var first = restTemplate.postForEntity("/api/demo/subscriptions", entity, Void.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        // Duplicate request - should return 409 Conflict
        var second = restTemplate.postForEntity("/api/demo/subscriptions", entity, ErrorResponse.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertNotNull(second.getBody());
        assertEquals("DUPLICATE_REQUEST", second.getBody().getCode());
    }

    @Test
    void shouldProcessDifferentRequestsWithDifferentKeys() {
        var headers1 = new HttpHeaders();
        headers1.set("Idempotent-Key", UUID.randomUUID().toString());
        headers1.setContentType(MediaType.APPLICATION_JSON);

        var headers2 = new HttpHeaders();
        headers2.set("Idempotent-Key", UUID.randomUUID().toString());
        headers2.setContentType(MediaType.APPLICATION_JSON);

        var request = new PaymentRequest(new BigDecimal("100.00"), "USD", "Test payment");

        var first = restTemplate.postForEntity("/api/demo/payments", new HttpEntity<>(request, headers1), PaymentResponse.class);
        var second = restTemplate.postForEntity("/api/demo/payments", new HttpEntity<>(request, headers2), PaymentResponse.class);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, second.getStatusCode());
        // Different idempotent keys should result in different transaction IDs
        assertNotNull(first.getBody());
        assertNotNull(second.getBody());
        assertNotEquals(first.getBody().getTransactionId(), second.getBody().getTransactionId());
    }

    @Test
    void shouldBypassIdempotencyWithReplayHeader() {
        var idempotentKey = UUID.randomUUID().toString();

        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new PaymentRequest(new BigDecimal("50.00"), "EUR", "Replay test");
        var entity = new HttpEntity<>(request, headers);

        // First request
        var first = restTemplate.postForEntity("/api/demo/payments", entity, PaymentResponse.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        // Request with Idempotent-Replay header - forces new execution
        var replayHeaders = new HttpHeaders();
        replayHeaders.set("Idempotent-Key", idempotentKey);
        replayHeaders.set("Idempotent-Replay", "true");
        replayHeaders.setContentType(MediaType.APPLICATION_JSON);

        var replayEntity = new HttpEntity<>(request, replayHeaders);
        var replay = restTemplate.postForEntity("/api/demo/payments", replayEntity, PaymentResponse.class);

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertNotNull(replay.getBody());
        // Replay creates a new transaction
        assertNotEquals(first.getBody().getTransactionId(), replay.getBody().getTransactionId());
    }

    @Test
    void shouldReturn400WhenIdempotentKeyHeaderIsMissing() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new PaymentRequest(new BigDecimal("10.00"), "USD", "No key");
        var response = restTemplate.postForEntity("/api/demo/payments",
                new HttpEntity<>(request, headers), ErrorResponse.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_REQUEST", response.getBody().getCode());
    }

    @Test
    void shouldAllowReplayForPreventRepeatedRequests() {
        var idempotentKey = UUID.randomUUID().toString();

        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new SubscribeRequest("replay@example.com", "Replay User");
        var entity = new HttpEntity<>(request, headers);

        var first = restTemplate.postForEntity("/api/demo/subscriptions", entity, Void.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        var second = restTemplate.postForEntity("/api/demo/subscriptions", entity, ErrorResponse.class);
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());

        var replayHeaders = new HttpHeaders();
        replayHeaders.set("Idempotent-Key", idempotentKey);
        replayHeaders.set("Idempotent-Replay", "true");
        replayHeaders.setContentType(MediaType.APPLICATION_JSON);

        var replay = restTemplate.postForEntity("/api/demo/subscriptions", new HttpEntity<>(request, replayHeaders), Void.class);
        assertEquals(HttpStatus.OK, replay.getStatusCode());
    }

    @Test
    void shouldReturn409WhenRequestInProgress() throws Exception {
        var idempotentKey = UUID.randomUUID().toString();

        var headers = new HttpHeaders();
        headers.set("Idempotent-Key", idempotentKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        var request = new PaymentRequest(new BigDecimal("20.00"), "USD", "Slow request");
        var entity = new HttpEntity<>(request, headers);

        var firstCall = CompletableFuture.supplyAsync(() ->
                restTemplate.postForEntity("/api/demo/payments/slow", entity, PaymentResponse.class));

        Thread.sleep(200);

        var secondCall = restTemplate.postForEntity("/api/demo/payments/slow", entity, ErrorResponse.class);
        var firstResult = firstCall.get();

        assertEquals(HttpStatus.OK, firstResult.getStatusCode());
        assertEquals(HttpStatus.CONFLICT, secondCall.getStatusCode());
        assertNotNull(secondCall.getBody());
        assertEquals("DUPLICATE_REQUEST", secondCall.getBody().getCode());
    }
}
