package com.example.openapi.controller;

import com.example.openapi.dto.ErrorResponse;
import com.example.openapi.petstore.api.StoreApi;
import com.example.openapi.petstore.model.Order;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Store", description = "Store order endpoints")
public class StoreController {

    private final StoreApi storeApi;

    public StoreController(StoreApi storeApi) {
        this.storeApi = storeApi;
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order found", content = @Content(schema = @Schema(implementation = Order.class)))
    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Order getOrderById(
            @Parameter(description = "Order ID", example = "1", required = true)
            @PathVariable @Positive(message = "orderId must be positive") Long orderId
    ) {
        return storeApi.getOrderById(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create order")
    @ApiResponse(responseCode = "201", description = "Order created", content = @Content(schema = @Schema(implementation = Order.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Order createOrder(@RequestBody Order order) {
        return storeApi.placeOrder(order);
    }

    @Deprecated
    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete order by ID", deprecated = true)
    @ApiResponse(responseCode = "204", description = "Order deleted")
    @ApiResponse(responseCode = "404", description = "Order not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void deleteOrder(
            @Parameter(description = "Order ID", example = "1", required = true)
            @PathVariable @Positive(message = "orderId must be positive") Long orderId
    ) {
        storeApi.deleteOrder(orderId);
    }

    @Hidden
    @GetMapping("/internal/inventory")
    public Map<String, Integer> hiddenInventory() {
        return storeApi.getInventory();
    }
}
