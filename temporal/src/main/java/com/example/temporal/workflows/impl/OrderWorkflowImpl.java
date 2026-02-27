package com.example.temporal.workflows.impl;

import com.example.temporal.activities.OrderActivities;
import com.example.temporal.activities.PaymentActivities;
import com.example.temporal.exception.OrderValidationException;
import com.example.temporal.workflows.InventoryChildWorkflow;
import com.example.temporal.workflows.OrderWorkflow;
import com.example.temporal.workflows.PaymentChildWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.common.SearchAttributeKey;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.CanceledFailure;
import io.temporal.workflow.Async;
import io.temporal.workflow.CancellationScope;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Implementation of the main order processing workflow.
 * <p>
 * KEY TEMPORAL CONCEPTS DEMONSTRATED:
 * <p>
 * 1. DETERMINISTIC EXECUTION
 * - Workflow code must be deterministic (same input → same output)
 * - No direct I/O operations (database calls, HTTP requests, random numbers, current time)
 * - Use Workflow.getLogger() for logging (replayed logs are suppressed)
 * - Use Workflow.sleep() instead of Thread.sleep()
 * - Use Workflow.currentTimeMillis() instead of System.currentTimeMillis()
 * <p>
 * 2. SAGA PATTERN
 * - Compensations are registered after each successful step
 * - On failure, compensations execute in reverse order
 * - Ensures data consistency across distributed services
 * <p>
 * 3. SIGNALS AND QUERIES
 * - Signals allow external input during workflow execution
 * - Queries allow reading workflow state without affecting execution
 * <p>
 * 4. SEARCH ATTRIBUTES
 * - Custom attributes for workflow discovery in Temporal UI
 * - Enable filtering workflows by business data
 * <p>
 * 5. WORKFLOW VERSIONING
 * - Workflow.getVersion() for backward-compatible changes
 * - Safe deployments without breaking running workflows
 */
@Slf4j
public class OrderWorkflowImpl implements OrderWorkflow {

    // Search attribute keys for workflow filtering
    private static final SearchAttributeKey<String> CUSTOMER_ID_KEY = SearchAttributeKey.forKeyword("CustomerId");
    private static final SearchAttributeKey<Long> ORDER_AMOUNT_KEY = SearchAttributeKey.forLong("OrderAmount");
    private static final SearchAttributeKey<String> ORDER_STATUS_KEY = SearchAttributeKey.forKeyword("OrderStatus");

    /**
     * Activity options with retry policy.
     * <p>
     * NON-RETRYABLE EXCEPTIONS:
     * OrderValidationException represents a permanent business rule failure.
     * There is no point retrying invalid input — the result is always the same.
     * Temporal will fail the activity immediately without consuming retry attempts.
     */
    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setMaximumAttempts(3)
                    .setDoNotRetry(OrderValidationException.class.getName())
                    .build())
            .build();

    /**
     * Child workflow options with retry policy.
     */
    private final ChildWorkflowOptions childWorkflowOptions = ChildWorkflowOptions.newBuilder()
            .setWorkflowExecutionTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(2)
                    .build())
            .build();

    // Activity stubs
    private final OrderActivities activities = Workflow.newActivityStub(OrderActivities.class, activityOptions);
    private final PaymentActivities paymentActivities = Workflow.newActivityStub(PaymentActivities.class, activityOptions);

    // Saga compensations list
    private final List<Runnable> compensations = new ArrayList<>();

    // Workflow state for signals and queries
    private OrderStatus status = OrderStatus.PENDING;
    private boolean cancelled = false;
    private String cancellationReason;
    private String shippingAddress;
    private int quantity = 1;

    // Store payment auth for compensation
    private String paymentAuthId;
    private String currentOrderId;
    private CancellationScope shippingReminderScope;
    private boolean shippingReminderCancelled;

    private static final EnumSet<OrderStatus> QUANTITY_MUTABLE_STATUSES = EnumSet.of(
            OrderStatus.PENDING,
            OrderStatus.VALIDATING,
            OrderStatus.PROCESSING_PAYMENT,
            OrderStatus.RESERVING_INVENTORY
    );

    @Override
    public String processOrder(String orderId, String customerId, long amount) {
        this.currentOrderId = orderId;

        log.info("Starting order processing for orderId={}, customerId={}, amount={}", orderId, customerId, amount);

        // Set search attributes for workflow discovery
        updateSearchAttributes(customerId, amount, "PROCESSING");

        try {
            // Step 1: Validate order (Activity)
            status = OrderStatus.VALIDATING;
            updateSearchAttributes(customerId, amount, status.name());
            log.info("Step 1: Validating order");

            // Version 1: Original validation
            // Version 2: Added fraud check before validation
            int version = Workflow.getVersion("FraudCheck", Workflow.DEFAULT_VERSION, 2);
            if (version >= 2) {
                log.info("Performing fraud check (v2 feature)");
                // In production: activities.checkFraud(customerId, amount);
            }

            activities.validateOrder(orderId, amount);
            log.info("Order validation completed successfully");

            // Activity result memoization demo:
            // Calling get() on the same promise multiple times reuses persisted result from history.
            var unitPricePromise = Async.function(activities::lookupPrice, orderId);
            var unitPrice = unitPricePromise.get();
            var paymentAmount = Math.multiplyExact(unitPrice, quantity);
            log.info("Price lookup complete: unitPrice={}, quantity={}, paymentAmount={}", unitPrice, quantity, paymentAmount);

            // Check for cancellation signal
            if (cancelled) {
                return handleCancellation(orderId);
            }

            // Step 2: Process payment via Child Workflow
            status = OrderStatus.PROCESSING_PAYMENT;
            updateSearchAttributes(customerId, amount, status.name());
            log.info("Step 2: Processing payment");

            var paymentWorkflow = Workflow.newChildWorkflowStub(PaymentChildWorkflow.class, childWorkflowOptions);
            var paymentResult = paymentWorkflow.processPayment(orderId, customerId, paymentAmount);
            log.info("Payment processing completed: {}", paymentResult);

            // Extract auth ID for potential refund (saga compensation)
            paymentAuthId = extractAuthId(paymentResult);

            // Register compensation for payment
            final var authIdForCompensation = paymentAuthId;
            final var amountForCompensation = amount;
            compensations.add(() -> {
                log.info("Saga compensation: Refunding payment authId={}", authIdForCompensation);
                paymentActivities.refundPayment(authIdForCompensation, amountForCompensation);
            });

            // Check for cancellation signal
            if (cancelled) {
                compensate();
                return handleCancellation(orderId);
            }

            // Step 3: Reserve inventory via Child Workflow
            status = OrderStatus.RESERVING_INVENTORY;
            updateSearchAttributes(customerId, amount, status.name());
            log.info("Step 3: Reserving inventory");

            var inventoryWorkflow = Workflow.newChildWorkflowStub(
                    InventoryChildWorkflow.class, childWorkflowOptions);
            var inventoryResult = inventoryWorkflow.reserveInventory(orderId, quantity);
            log.info("Inventory reservation completed: {}", inventoryResult);

            // Register compensation for inventory
            final String orderIdForCompensation = orderId;
            final int quantityForCompensation = quantity;
            compensations.add(() -> {
                log.info("Saga compensation: Releasing inventory orderId={}", orderIdForCompensation);
                activities.releaseInventory(orderIdForCompensation, quantityForCompensation);
            });

            // Check for cancellation signal
            if (cancelled) {
                compensate();
                return handleCancellation(orderId);
            }

            // Step 4: Send confirmation notification (Activity)
            status = OrderStatus.SENDING_NOTIFICATION;
            updateSearchAttributes(customerId, amount, status.name());
            log.info("Step 4: Sending confirmation");

            var notificationMessage = "Order " + orderId + " confirmed!";
            if (shippingAddress != null) {
                notificationMessage += " Shipping to: " + shippingAddress;
            }
            notificationMessage += " Unit price: " + unitPricePromise.get() + ", quantity: " + quantity;
            activities.sendNotification(customerId, notificationMessage);
            log.info("Notification sent successfully");

            // WORKFLOW TIMER DEMO: durable sleep before shipping reminder.
            // Workflow.sleep() is fundamentally different from Thread.sleep():
            // - It is persisted in Temporal's event history
            // - It survives worker restarts and process failures
            // - Time is skipped instantly in TestWorkflowEnvironment (no real waiting)
            // - Use Workflow.currentTimeMillis() (not System.currentTimeMillis()) for time reads
            shippingReminderScope = Workflow.newCancellationScope(() -> {
                log.info("Timer started: shipping reminder will fire in 24 hours (skipped in tests)");
                Workflow.sleep(Duration.ofHours(24));
                activities.sendNotification(customerId, "Your order " + orderId + " ships today!");
            });
            try {
                shippingReminderScope.run();
            } catch (CanceledFailure e) {
                shippingReminderCancelled = true;
                log.info("Shipping reminder scope cancelled for orderId={}", orderId);
            } finally {
                shippingReminderScope = null;
            }

            // Success - clear compensations (no longer needed)
            compensations.clear();

            status = OrderStatus.COMPLETED;
            updateSearchAttributes(customerId, amount, status.name());

            var result = "Order " + orderId + " processed successfully";
            log.info("Order processing completed: {}", result);
            return result;

        } catch (ActivityFailure e) {
            log.error("Activity failed after retries for orderId={}: {}", orderId, e.getMessage());
            status = OrderStatus.FAILED;
            updateSearchAttributes(customerId, amount, status.name());

            // Execute saga compensations
            compensate();

            throw e;
        } catch (Exception e) {
            log.error("Workflow failed for orderId={}: {}", orderId, e.getMessage());
            status = OrderStatus.FAILED;
            updateSearchAttributes(customerId, amount, status.name());

            // Execute saga compensations
            compensate();

            throw e;
        }
    }

    @Override
    public void cancelOrder(String reason) {
        log.info("Received cancel signal with reason: {}", reason);

        if (shippingReminderScope != null) {
            shippingReminderScope.cancel(reason);
            shippingReminderCancelled = true;
            log.info("Cancelled shipping reminder scope for orderId={}", currentOrderId);
            return;
        }

        this.cancelled = true;
        this.cancellationReason = reason;
    }

    @Override
    public void updateShippingAddress(String newAddress) {
        log.info("Received shipping address update: {}", newAddress);
        this.shippingAddress = newAddress;
    }

    @Override
    public int updateQuantity(String orderId, int newQuantity) {
        if (!activities.checkInventory(orderId, newQuantity)) {
            throw new IllegalArgumentException("insufficient inventory for quantity " + newQuantity);
        }
        this.quantity = newQuantity;
        log.info("Accepted quantity update: orderId={}, quantity={}", orderId, newQuantity);
        return this.quantity;
    }

    @Override
    public void validateUpdateQuantity(String orderId, int newQuantity) {
        if (!orderId.equals(currentOrderId)) {
            throw new IllegalArgumentException("orderId mismatch for quantity update");
        }
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        if (!QUANTITY_MUTABLE_STATUSES.contains(status)) {
            throw new IllegalStateException("quantity cannot be updated in status " + status);
        }
    }

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public String getShippingAddress() {
        return shippingAddress;
    }

    /**
     * Execute saga compensations in reverse order.
     */
    private void compensate() {
        if (compensations.isEmpty()) {
            log.info("No compensations to execute");
            return;
        }

        log.info("Executing {} saga compensations in reverse order", compensations.size());
        Collections.reverse(compensations);

        for (Runnable compensation : compensations) {
            try {
                compensation.run();
            } catch (Exception e) {
                // Log but continue with other compensations
                log.error("Compensation failed: {}", e.getMessage());
            }
        }

        compensations.clear();
        log.info("Saga compensations completed");
    }

    /**
     * Handle order cancellation.
     */
    private String handleCancellation(String orderId) {
        status = OrderStatus.CANCELLED;
        var message = "Order " + orderId + " cancelled: " + cancellationReason;
        if (shippingReminderCancelled) {
            message += " (shipping reminder cancelled)";
        }
        log.info(message);
        return message;
    }

    /**
     * Extract authorization ID from payment result.
     */
    private String extractAuthId(String paymentResult) {
        // Payment result format: "Payment successful: AUTH-XXXXXXXX"
        if (paymentResult != null && paymentResult.contains("AUTH-")) {
            int start = paymentResult.indexOf("AUTH-");
            return paymentResult.substring(start);
        }
        return "UNKNOWN";
    }

    /**
     * Update search attributes for workflow discovery.
     */
    private void updateSearchAttributes(String customerId, long amount, String statusValue) {
        try {
            Workflow.upsertTypedSearchAttributes(
                    CUSTOMER_ID_KEY.valueSet(customerId),
                    ORDER_AMOUNT_KEY.valueSet(amount),
                    ORDER_STATUS_KEY.valueSet(statusValue)
            );
        } catch (Exception e) {
            // Search attributes may not be configured - log and continue
            log.debug("Could not update search attributes: {}", e.getMessage());
        }
    }
}
