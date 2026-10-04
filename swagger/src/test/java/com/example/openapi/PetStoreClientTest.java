package com.example.openapi;

import com.example.openapi.exception.PetNotFoundException;
import com.example.openapi.exception.UpstreamClientException;
import com.example.openapi.exception.UpstreamServiceException;
import com.example.openapi.feign.CorrelationIdInterceptor;
import com.example.openapi.petstore.api.PetApi;
import com.example.openapi.petstore.api.StoreApi;
import com.example.openapi.petstore.model.Pet;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the generated Feign clients as wired by {@code PetStoreConfig} against a local HTTP server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PetStoreClientTest {

    private static final HttpServer SERVER = startServer();
    private static final List<HttpExchange> REQUESTS = new CopyOnWriteArrayList<>();

    @DynamicPropertySource
    static void petStoreProperties(DynamicPropertyRegistry registry) {
        registry.add("app.pet-store.base-url", () -> "http://localhost:" + SERVER.getAddress().getPort() + "/v2");
        registry.add("app.pet-store.username", () -> "alice");
        registry.add("app.pet-store.password", () -> "secret");
    }

    @Autowired
    private PetApi petApi;

    @Autowired
    private StoreApi storeApi;

    @BeforeEach
    void clearRequests() {
        REQUESTS.clear();
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @Test
    void sendsBasicAuthAndCorrelationIdHeaders() {
        var pet = petApi.getPetById(1L);

        assertThat(pet.getName()).isEqualTo("Buddy");
        assertThat(pet.getStatus()).isEqualTo(Pet.StatusEnum.AVAILABLE);

        var headers = REQUESTS.getFirst().getRequestHeaders();
        var expectedAuth = "Basic " + Base64.getEncoder().encodeToString("alice:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(headers.getFirst("Authorization")).isEqualTo(expectedAuth);
        assertThat(headers.getFirst(CorrelationIdInterceptor.CORRELATION_ID_HEADER)).isNotBlank();
    }

    @Test
    void decodesDateTimeFields() {
        var order = storeApi.getOrderById(1L);

        assertThat(order.getPetId()).isEqualTo(1L);
        assertThat(order.getShipDate()).isNotNull();
        assertThat(order.getShipDate().getYear()).isEqualTo(2026);
    }

    @Test
    void mapsUpstreamErrorsToDomainExceptions() {
        assertThatThrownBy(() -> petApi.getPetById(404L)).isInstanceOf(PetNotFoundException.class);
        assertThatThrownBy(() -> petApi.getPetById(400L)).isInstanceOf(UpstreamClientException.class);
        assertThatThrownBy(() -> petApi.getPetById(500L)).isInstanceOf(UpstreamServiceException.class);
    }

    private static HttpServer startServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/v2/pet/", exchange -> {
                REQUESTS.add(exchange);
                var id = exchange.getRequestURI().getPath().substring("/v2/pet/".length());
                switch (id) {
                    case "1" -> respond(exchange, 200, """
                            {"id": 1, "name": "Buddy", "photoUrls": [], "status": "available"}
                            """);
                    case "400", "404", "500" -> respond(exchange, Integer.parseInt(id), "{}");
                    default -> respond(exchange, 404, "{}");
                }
            });
            // Petstore serialises dates with a zone offset, e.g. 2026-10-04T10:15:30.000+0000
            server.createContext("/v2/store/order/", exchange -> {
                REQUESTS.add(exchange);
                respond(exchange, 200, """
                        {"id": 1, "petId": 1, "quantity": 2, "shipDate": "2026-10-04T10:15:30.000+0000", "status": "placed", "complete": false}
                        """);
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
