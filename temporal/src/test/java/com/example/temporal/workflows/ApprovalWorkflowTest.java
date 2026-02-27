package com.example.temporal.workflows;

import com.example.temporal.activities.OrderActivities;
import com.example.temporal.workflows.impl.ApprovalWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovalWorkflowTest {

    private static final String TASK_QUEUE = "test-approval-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(new NoopOrderActivities());
        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void approveSignal_returnsApproved() {
        ApprovalWorkflow workflow = newWorkflow("approval-approve");
        WorkflowClient.start(workflow::requestApproval, "ORD-1", "CUST-1", 1000L);

        workflow.approve("approved by manager");
        String result = WorkflowStub.fromTyped(workflow).getResult(String.class);

        assertThat(result).contains("approved");
        assertThat(workflow.getApprovalStatus()).isEqualTo("APPROVED");
    }

    @Test
    void rejectSignal_returnsRejected() {
        ApprovalWorkflow workflow = newWorkflow("approval-reject");
        WorkflowClient.start(workflow::requestApproval, "ORD-2", "CUST-1", 1000L);

        workflow.reject("too expensive");
        String result = WorkflowStub.fromTyped(workflow).getResult(String.class);

        assertThat(result).contains("rejected");
        assertThat(workflow.getApprovalStatus()).isEqualTo("REJECTED");
    }

    @Test
    void noSignal_timeoutAutoRejected() {
        ApprovalWorkflow workflow = newWorkflow("approval-timeout");
        WorkflowClient.start(workflow::requestApproval, "ORD-3", "CUST-1", 1000L);

        testEnv.sleep(Duration.ofHours(25));
        String result = WorkflowStub.fromTyped(workflow).getResult(String.class);

        assertThat(result).contains("timeout");
        assertThat(workflow.getApprovalStatus()).isEqualTo("AUTO_REJECTED");
    }

    private ApprovalWorkflow newWorkflow(String workflowId) {
        return client.newWorkflowStub(
                ApprovalWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId(workflowId)
                        .build());
    }

    static class NoopOrderActivities implements OrderActivities {
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
}
