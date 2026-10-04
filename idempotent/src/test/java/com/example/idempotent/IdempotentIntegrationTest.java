package com.example.idempotent;

import com.example.idempotent.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class IdempotentIntegrationTest {

    private static final String KEY_HEADER = "Idempotent-Key";
    private static final String REPLAY_HEADER = "Idempotent-Replay";

    @Autowired
    private RestTestClient restTestClient;

    private PaymentResponse postPayment(String uri, String idempotentKey, boolean replay, PaymentRequest request) {
        return restTestClient.post().uri(uri)
                .header(KEY_HEADER, idempotentKey)
                .header(REPLAY_HEADER, String.valueOf(replay))
                .body(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(PaymentResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void shouldReturnCachedResponseOnDuplicatePaymentRequest() {
        var idempotentKey = UUID.randomUUID().toString();
        var request = new PaymentRequest(new BigDecimal("100.00"), "USD", "Test payment");

        // First request - processes payment
        var first = postPayment("/api/demo/payments", idempotentKey, false, request);
        assertNotNull(first);
        assertNotNull(first.getTransactionId());

        // Duplicate request - should return same response (cached)
        var second = postPayment("/api/demo/payments", idempotentKey, false, request);
        assertNotNull(second);
        assertEquals(first.getTransactionId(), second.getTransactionId());
    }

    @Test
    void shouldReturnCachedResponseOnDuplicateOrderRequest() {
        var idempotentKey = UUID.randomUUID().toString();
        var items = List.of(new OrderItem("PROD-001", "Test Product", 2, new BigDecimal("50.00")));
        var request = new OrderRequest(items, "123 Main St");

        // First request - creates order
        var first = restTestClient.post().uri("/api/demo/orders")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(first);
        assertNotNull(first.getOrderId());
        assertEquals(new BigDecimal("100.00"), first.getTotal());

        // Duplicate request - should return same response (cached) with the original 201 status
        var second = restTestClient.post().uri("/api/demo/orders")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult()
                .getResponseBody();
        assertNotNull(second);
        assertEquals(first.getOrderId(), second.getOrderId());
        assertEquals(first.getItems(), second.getItems());
        assertEquals(first.getCreatedAt(), second.getCreatedAt());
    }

    @Test
    void shouldPreserveNoContentStatusOnDuplicateOrderCancelRequest() {
        var idempotentKey = UUID.randomUUID().toString();

        restTestClient.delete().uri("/api/demo/orders/order-42")
                .header(KEY_HEADER, idempotentKey)
                .exchange()
                .expectStatus().isNoContent();

        restTestClient.delete().uri("/api/demo/orders/order-42")
                .header(KEY_HEADER, idempotentKey)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void shouldReturn409WhenSubscriptionDuplicated() {
        var idempotentKey = UUID.randomUUID().toString();
        var request = new SubscribeRequest("test@example.com", "Test User");

        // First request - subscribes
        restTestClient.post().uri("/api/demo/subscriptions")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isOk();

        // Duplicate request - should return 409 Conflict
        restTestClient.post().uri("/api/demo/subscriptions")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_REQUEST");
    }

    @Test
    void shouldProcessDifferentRequestsWithDifferentKeys() {
        var request = new PaymentRequest(new BigDecimal("100.00"), "USD", "Test payment");

        var first = postPayment("/api/demo/payments", UUID.randomUUID().toString(), false, request);
        var second = postPayment("/api/demo/payments", UUID.randomUUID().toString(), false, request);

        // Different idempotent keys should result in different transaction IDs
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.getTransactionId(), second.getTransactionId());
    }

    @Test
    void shouldBypassIdempotencyWithReplayHeader() {
        var idempotentKey = UUID.randomUUID().toString();
        var request = new PaymentRequest(new BigDecimal("50.00"), "EUR", "Replay test");

        // First request
        var first = postPayment("/api/demo/payments", idempotentKey, false, request);

        // Request with Idempotent-Replay header - forces new execution
        var replay = postPayment("/api/demo/payments", idempotentKey, true, request);

        assertNotNull(first);
        assertNotNull(replay);
        // Replay creates a new transaction
        assertNotEquals(first.getTransactionId(), replay.getTransactionId());
    }

    @Test
    void shouldReturn400WhenIdempotentKeyHeaderIsMissing() {
        var request = new PaymentRequest(new BigDecimal("10.00"), "USD", "No key");

        restTestClient.post().uri("/api/demo/payments")
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void shouldAllowReplayForPreventRepeatedRequests() {
        var idempotentKey = UUID.randomUUID().toString();
        var request = new SubscribeRequest("replay@example.com", "Replay User");

        restTestClient.post().uri("/api/demo/subscriptions")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isOk();

        restTestClient.post().uri("/api/demo/subscriptions")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        restTestClient.post().uri("/api/demo/subscriptions")
                .header(KEY_HEADER, idempotentKey)
                .header(REPLAY_HEADER, "true")
                .body(request)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void shouldReturn409WhenRequestInProgress() throws Exception {
        var idempotentKey = UUID.randomUUID().toString();
        var request = new PaymentRequest(new BigDecimal("20.00"), "USD", "Slow request");

        var firstCall = CompletableFuture.supplyAsync(() ->
                postPayment("/api/demo/payments/slow", idempotentKey, false, request));

        Thread.sleep(200);

        restTestClient.post().uri("/api/demo/payments/slow")
                .header(KEY_HEADER, idempotentKey)
                .body(request)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_REQUEST");

        assertNotNull(firstCall.get().getTransactionId());
    }
}
