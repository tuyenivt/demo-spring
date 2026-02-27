package com.example.temporal.controller;

import com.example.temporal.config.TemporalProperties;
import com.example.temporal.dto.ErrorResponse;
import com.example.temporal.workflows.CartWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/carts")
public class CartController {

    private final TemporalProperties temporalProperties;
    private final WorkflowClient workflowClient;

    /**
     * SignalWithStart: atomically start cart workflow or signal existing one.
     */
    @PostMapping("/{cartId}/items")
    public ResponseEntity<?> addItem(@PathVariable String cartId, @RequestBody Map<String, String> body) {
        var item = body.get("item");
        if (item == null || item.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("item is required"));
        }

        var workflowId = "cart-" + cartId;
        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(temporalProperties.getTaskQueue())
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofHours(2))
                .build();

        try {
            var workflow = workflowClient.newWorkflowStub(CartWorkflow.class, options);
            WorkflowStub.fromTyped(workflow).signalWithStart(
                    "addItem",
                    new Object[]{item},
                    new Object[]{cartId}
            );

            var items = workflow.getItems();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "cartId", cartId,
                    "workflowId", workflowId,
                    "items", items
            ));
        } catch (Exception e) {
            log.error("Failed to add item to cart: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to add item: " + e.getMessage()));
        }
    }
}
