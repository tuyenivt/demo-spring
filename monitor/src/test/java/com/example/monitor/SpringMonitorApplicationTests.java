package com.example.monitor;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        // Keep tracing on but don't ship spans to a Zipkin server during tests
        properties = "management.tracing.export.enabled=false"
)
@AutoConfigureRestTestClient
@AutoConfigureMetrics
@AutoConfigureTracing
class SpringMonitorApplicationTests {

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private Tracer tracer;

    @Test
    void healthEndpointReturnsUp() {
        restTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void readinessProbeIncludesDbAndExternalApi() {
        restTestClient.get().uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.components.db.status").isEqualTo("UP")
                .jsonPath("$.components.externalApi.status").isEqualTo("UP");
    }

    @Test
    void prometheusEndpointContainsCustomMetrics() {
        restTestClient.get().uri("/customers").exchange().expectStatus().isOk();
        restTestClient.get().uri("/customers/transform").exchange().expectStatus().isOk();

        var body = restTestClient.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
        assertThat(body).contains("customer_access_total", "customer_transform_seconds", "customer_count");
    }

    @Test
    void metricsEndpointListsExpectedMetrics() {
        restTestClient.get().uri("/customers").exchange().expectStatus().isOk();

        restTestClient.get().uri("/actuator/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.names").value(names -> assertThat(names.toString()).contains("customer.access", "db.query"));
    }

    @Test
    void tracingIsAutoConfigured() {
        assertThat(tracer).isNotSameAs(Tracer.NOOP);
        var span = tracer.nextSpan().name("test-span").start();
        try {
            assertThat(span.context().traceId()).isNotBlank();
        } finally {
            span.end();
        }
    }
}
