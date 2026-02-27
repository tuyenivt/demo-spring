package com.example.temporal.controller;

import com.example.temporal.config.TemporalProperties;
import com.example.temporal.dto.ErrorResponse;
import com.example.temporal.workflows.NotificationWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final TemporalProperties temporalProperties;
    private final WorkflowClient workflowClient;

    @PostMapping("/send")
    public ResponseEntity<?> sendAll(@RequestBody Map<String, String> body) {
        var customerId = body.get("customerId");
        var message = body.get("message");

        if (customerId == null || customerId.isBlank() || message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("customerId and message are required"));
        }

        var workflowId = "notification-" + UUID.randomUUID();
        var options = WorkflowOptions.newBuilder()
                .setTaskQueue(temporalProperties.getTaskQueue())
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(2))
                .build();

        try {
            var workflow = workflowClient.newWorkflowStub(NotificationWorkflow.class, options);
            var result = workflow.sendAll(customerId, message);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                    "workflowId", workflowId,
                    "result", result
            ));
        } catch (Exception e) {
            log.error("Failed sending fan-out notifications: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to send notifications: " + e.getMessage()));
        }
    }
}
