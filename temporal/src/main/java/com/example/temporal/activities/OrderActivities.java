package com.example.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity interface for order-related operations.
 * <p>
 * TEMPORAL CONCEPT: Activities
 * <p>
 * Activities are where you perform actual work:
 * - Database operations
 * - HTTP calls to external services
 * - File I/O
 * - Any non-deterministic operations
 * <p>
 * KEY DIFFERENCES FROM WORKFLOWS:
 * <p>
 * 1. ACTIVITIES CAN:
 * - Perform I/O operations
 * - Access databases
 * - Call external APIs
 * - Use current system time
 * - Generate random numbers
 * - Be non-deterministic
 * <p>
 * 2. WORKFLOWS CANNOT:
 * - Do any of the above directly
 * - Must use activities for all I/O
 * - Must be deterministic (same input → same output)
 * <p>
 * WHY THIS SEPARATION?
 * - Temporal can replay workflow code during recovery
 * - Non-deterministic code in workflows would produce different results on replay
 * - Activities are executed once, results are recorded in history
 */
@ActivityInterface
public interface OrderActivities {

    /**
     * Validates order details.
     * In production: check business rules, validate customer, etc.
     */
    @ActivityMethod
    void validateOrder(String orderId, long amount);

    /**
     * Checks inventory availability.
     * In production: query inventory database or service.
     */
    @ActivityMethod
    boolean checkInventory(String orderId, int quantity);

    /**
     * Reserves inventory items.
     * In production: update inventory database with reservation.
     */
    @ActivityMethod
    void reserveInventory(String orderId, int quantity);

    /**
     * Releases previously reserved inventory (saga compensation).
     * In production: update inventory database to release reservation.
     */
    @ActivityMethod
    void releaseInventory(String orderId, int quantity);

    /**
     * Sends notification to customer.
     * In production: send email, SMS, or push notification.
     */
    @ActivityMethod
    void sendNotification(String customerId, String message);

    /**
     * Looks up the current unit price for an order.
     * Demonstrates activity result memoization when a workflow reuses the same Promise result.
     */
    @ActivityMethod
    long lookupPrice(String orderId);
}
