package com.example.temporal.workflows;

import com.example.temporal.activities.OrderActivities;
import com.example.temporal.activities.PaymentActivities;
import com.example.temporal.workflows.impl.*;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderWorkflowTest {

    private static final String TASK_QUEUE = "test-order-queue";

    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        testEnv.registerSearchAttribute("CustomerId", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        testEnv.registerSearchAttribute("OrderAmount", IndexedValueType.INDEXED_VALUE_TYPE_INT);
        testEnv.registerSearchAttribute("OrderStatus", IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);

        worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                OrderWorkflowImpl.class,
                PaymentChildWorkflowImpl.class,
                InventoryChildWorkflowImpl.class,
                ReportWorkflowImpl.class,
                PollingWorkflowImpl.class,
                ApprovalWorkflowImpl.class,
                NotificationWorkflowImpl.class,
                CartWorkflowImpl.class
        );

        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void processOrder_success() {
        worker.registerActivitiesImplementations(
                new SuccessOrderActivities(),
                new SuccessPaymentActivities()
        );
        testEnv.start();

        var workflow = newOrderWorkflow();
        String result = workflow.processOrder("ORD-123", "CUST-456", 1000L);

        assertThat(result).contains("ORD-123").contains("successfully");
    }

    @Test
    void processOrder_validationFailure() {
        worker.registerActivitiesImplementations(
                new FailingValidationActivities(),
                new SuccessPaymentActivities()
        );
        testEnv.start();

        var workflow = newOrderWorkflow();

        assertThatThrownBy(() -> workflow.processOrder("ORD-123", "CUST-456", -100L))
                .isInstanceOf(WorkflowFailedException.class)
                .rootCause()
                .hasMessageContaining("Invalid amount");
    }

    @Test
    void updateShippingAddress_signal_updatesAddress() {
        worker.registerActivitiesImplementations(
                new SlowOrderActivities(),
                new SuccessPaymentActivities()
        );
        testEnv.start();

        var workflow = newOrderWorkflow();
        WorkflowClient.start(workflow::processOrder, "ORD-ADDR", "CUST-1", 1000L);

        workflow.updateShippingAddress("123 New Street, Springfield");
        assertThat(workflow.getShippingAddress()).isEqualTo("123 New Street, Springfield");
    }

    @Test
    void updateQuantity_acceptsValidUpdate() {
        worker.registerActivitiesImplementations(
                new SlowOrderActivities(),
                new SuccessPaymentActivities()
        );
        testEnv.start();

        var workflow = newOrderWorkflow();
        WorkflowClient.start(workflow::processOrder, "ORD-UPD-OK", "CUST-1", 1000L);

        int acceptedQuantity = workflow.updateQuantity("ORD-UPD-OK", 3);
        String result = WorkflowStub.fromTyped(workflow).getResult(String.class);

        assertThat(acceptedQuantity).isEqualTo(3);
        assertThat(result).contains("successfully");
    }

    @Test
    void updateQuantity_rejectsInvalidUpdate() {
        worker.registerActivitiesImplementations(
                new SlowOrderActivities(),
                new SuccessPaymentActivities()
        );
        testEnv.start();

        var workflow = newOrderWorkflow();
        WorkflowClient.start(workflow::processOrder, "ORD-UPD-BAD", "CUST-1", 1000L);

        assertThatThrownBy(() -> workflow.updateQuantity("ORD-UPD-BAD", 0))
                .rootCause()
                .hasMessageContaining("quantity must be greater than 0");
    }

    @Test
    void inventoryFailureAfterPayment_triggersRefundCompensation() {
        var trackingOrderActivities = new TrackingOrderActivities();
        var trackingPaymentActivities = new SuccessTrackingPaymentActivities();

        worker.registerActivitiesImplementations(
                trackingOrderActivities,
                trackingPaymentActivities
        );
        testEnv.start();

        var workflow = newOrderWorkflow();

        assertThatThrownBy(() -> workflow.processOrder("ORD-INV-FAIL", "CUST-1", 1000L))
                .isInstanceOf(WorkflowFailedException.class);

        assertThat(trackingPaymentActivities.refundCount.get()).isEqualTo(1);
    }

    @Test
    void cancelOrder_duringReminderScope_skipsReminder() {
        var trackingOrderActivities = new ReminderTrackingOrderActivities();
        worker.registerActivitiesImplementations(
                trackingOrderActivities,
                new SuccessPaymentActivities()
        );
        testEnv.start();

        var workflow = newOrderWorkflow();
        WorkflowClient.start(workflow::processOrder, "ORD-REMINDER", "CUST-1", 1000L);

        for (int i = 0; i < 10 && trackingOrderActivities.notificationCount.get() == 0; i++) {
            testEnv.sleep(Duration.ofSeconds(1));
        }

        workflow.cancelOrder("cancel reminder only");
        String result = WorkflowStub.fromTyped(workflow).getResult(String.class);

        assertThat(result).contains("successfully");
        assertThat(trackingOrderActivities.notificationCount.get()).isEqualTo(1);
    }

    private OrderWorkflow newOrderWorkflow() {
        return client.newWorkflowStub(
                OrderWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    }

    public static class SuccessOrderActivities implements OrderActivities {
        @Override
        public void validateOrder(String orderId, long amount) {
        }

        @Override
        public boolean checkInventory(String orderId, int quantity) {
            return true;
        }

        @Override
        public void reserveInventory(String orderId, int quantity) {
        }

        @Override
        public void releaseInventory(String orderId, int quantity) {
        }

        @Override
        public void sendNotification(String customerId, String message) {
        }

        @Override
        public long lookupPrice(String orderId) {
            return 1000L;
        }
    }

    public static class FailingValidationActivities extends SuccessOrderActivities {
        @Override
        public void validateOrder(String orderId, long amount) {
            throw new IllegalArgumentException("Invalid amount: " + amount);
        }
    }

    public static class SlowOrderActivities extends SuccessOrderActivities {
        @Override
        public void validateOrder(String orderId, long amount) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static class ReminderTrackingOrderActivities extends SuccessOrderActivities {
        final AtomicInteger notificationCount = new AtomicInteger(0);

        @Override
        public void sendNotification(String customerId, String message) {
            notificationCount.incrementAndGet();
        }
    }

    public static class TrackingOrderActivities extends SuccessOrderActivities {
        @Override
        public void reserveInventory(String orderId, int quantity) {
            throw new RuntimeException("Inventory system unavailable");
        }
    }

    public static class SuccessPaymentActivities implements PaymentActivities {
        @Override
        public String authorizePayment(String customerId, long amount) {
            return "AUTH-12345678";
        }

        @Override
        public void capturePayment(String authorizationId, long amount) {
        }

        @Override
        public void refundPayment(String authorizationId, long amount) {
        }
    }

    public static class SuccessTrackingPaymentActivities extends SuccessPaymentActivities {
        final AtomicInteger refundCount = new AtomicInteger(0);

        @Override
        public void refundPayment(String authorizationId, long amount) {
            refundCount.incrementAndGet();
        }
    }
}
