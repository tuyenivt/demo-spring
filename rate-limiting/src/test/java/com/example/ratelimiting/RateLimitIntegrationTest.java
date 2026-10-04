package com.example.ratelimiting;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class RateLimitIntegrationTest {

    @Autowired
    RestTestClient restTestClient;

    @Test
    void shouldAllowRequestsWithinLimit() {
        for (int i = 0; i < 5; i++) {
            restTestClient.get().uri("/api/orders")
                    .header("X-USER-ID", "test-user-within-limit")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("X-RateLimit-Limit", "5")
                    .expectHeader().exists("X-RateLimit-Remaining");
        }
    }

    @Test
    void shouldRejectRequestsExceedingLimit() {
        // Exhaust the limit
        for (int i = 0; i < 5; i++) {
            restTestClient.get().uri("/api/orders")
                    .header("X-USER-ID", "rate-limit-user-exceeding")
                    .exchange()
                    .expectStatus().isOk();
        }

        // Next request should be rejected
        restTestClient.get().uri("/api/orders")
                .header("X-USER-ID", "rate-limit-user-exceeding")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.error").isEqualTo("Too Many Requests");
    }

    @Test
    void shouldAllowAnonymousUsersWithIpBasedRateLimit() {
        restTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-RateLimit-Limit", "5");
    }

    @Test
    void shouldReturnRateLimitHeaders() {
        restTestClient.get().uri("/api/orders")
                .header("X-USER-ID", "header-test-user")
                .exchange()
                .expectHeader().valueEquals("X-RateLimit-Limit", "5")
                .expectHeader().exists("X-RateLimit-Remaining")
                .expectHeader().exists("X-RateLimit-Reset");
    }

    @Test
    void shouldAllowNoRateLimitEndpoint() {
        for (int i = 0; i < 10; i++) {
            restTestClient.get().uri("/api/hello")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Test
    void shouldHandleConcurrentRequests() {
        // Use a unique user per test run to avoid interference from other tests
        var userId = "concurrent-user-" + UUID.randomUUID();

        int totalRequests = 20;
        try (var executor = Executors.newFixedThreadPool(totalRequests)) {
            var futures = IntStream.range(0, totalRequests)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> restTestClient.get().uri("/api/orders")
                            .header("X-USER-ID", userId)
                            .exchange()
                            .returnResult(String.class)
                            .getStatus(), executor))
                    .toList();

            var statuses = futures.stream().map(CompletableFuture::join).toList();

            assertThat(statuses).filteredOn(HttpStatusCode::is2xxSuccessful).hasSize(5);
            assertThat(statuses).filteredOn(s -> s.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)).hasSize(15);
        }
    }

    @Test
    void shouldReturnStatusWithoutConsumingToken() {
        var userId = "status-check-user-" + UUID.randomUUID();

        // First, consume one token via real endpoint
        restTestClient.get().uri("/api/orders")
                .header("X-USER-ID", userId)
                .exchange()
                .expectStatus().isOk();

        // Status endpoint should reflect remaining without consuming further
        for (int i = 0; i < 2; i++) {
            restTestClient.get().uri("/api/rate-limit/status?profile=strict")
                    .header("X-USER-ID", userId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.profile").isEqualTo("strict")
                    .jsonPath("$.limit").isEqualTo(5)
                    .jsonPath("$.remaining").exists();
        }
    }
}
