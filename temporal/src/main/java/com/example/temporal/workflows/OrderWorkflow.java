package com.example.temporal.workflows;

import io.temporal.workflow.*;

/**
 * Main workflow interface for order processing.
 * <p>
 * TEMPORAL CONCEPT: Workflow Interface
 * - Defines the contract for workflow execution
 * - Must be deterministic - same input always produces same output
 * - Cannot perform I/O operations directly (must use Activities)
 * - Can orchestrate Activities and Child Workflows
 * <p>
 * SIGNALS: Async messages to running workflow to modify state
 * QUERIES: Read-only access to workflow state without affecting execution
 */
@WorkflowInterface
public interface OrderWorkflow {

    /**
     * Main workflow method that processes an order.
     *
     * @param orderId    Unique order identifier
     * @param customerId Customer identifier
     * @param amount     Order amount in cents
     * @return Order processing result
     */
    @WorkflowMethod
    String processOrder(String orderId, String customerId, long amount);

    /**
     * Signal to cancel the order.
     * <p>
     * TEMPORAL CONCEPT: Signals
     * - Async messages sent to running workflow
     * - Can modify workflow state
     * - Workflow checks for signals between steps
     *
     * @param reason Reason for cancellation
     */
    @SignalMethod
    void cancelOrder(String reason);

    /**
     * Signal to update shipping address.
     *
     * @param newAddress New shipping address
     */
    @SignalMethod
    void updateShippingAddress(String newAddress);

    /**
     * Update quantity while workflow is processing.
     * Unlike signals, updates are synchronous and can be rejected by validators.
     *
     * @return accepted quantity
     */
    @UpdateMethod
    int updateQuantity(String orderId, int newQuantity);

    /**
     * Validator for quantity updates.
     * Throws to reject invalid updates before state mutation.
     */
    @UpdateValidatorMethod(updateName = "updateQuantity")
    void validateUpdateQuantity(String orderId, int newQuantity);

    /**
     * Query current order status.
     * <p>
     * TEMPORAL CONCEPT: Queries
     * - Read-only access to workflow state
     * - Does not affect workflow execution
     * - Can be called anytime during workflow lifecycle
     *
     * @return Current order status
     */
    @QueryMethod
    OrderStatus getStatus();

    /**
     * Query current shipping address.
     *
     * @return Current shipping address or null if not set
     */
    @QueryMethod
    String getShippingAddress();

    /**
     * Order status enum for tracking workflow progress.
     */
    enum OrderStatus {
        PENDING,
        VALIDATING,
        PROCESSING_PAYMENT,
        RESERVING_INVENTORY,
        SENDING_NOTIFICATION,
        COMPLETED,
        CANCELLED,
        FAILED
    }
}
