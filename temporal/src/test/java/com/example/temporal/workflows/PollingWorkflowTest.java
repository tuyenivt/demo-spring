package com.example.temporal.workflows;

import com.example.temporal.activities.OrderActivities;
import com.example.temporal.workflows.impl.PollingWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class PollingWorkflowTest {

    private static final String TASK_QUEUE = "test-polling-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(PollingWorkflowImpl.class);
        worker.registerActivitiesImplementations(new NoopOrderActivities());
        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void queryIterationCount_midRun() {
        PollingWorkflow workflow = newWorkflow("polling-query");
        WorkflowClient.start(workflow::startPolling, "target-1", 0);

        testEnv.sleep(Duration.ofSeconds(12));
        assertThat(workflow.getIterationCount()).isGreaterThan(0);
    }

    @Test
    void completesAtMultipleOf25() {
        PollingWorkflow workflow = newWorkflow("polling-stop");
        String result = workflow.startPolling("target-2", 0);

        assertThat(result).contains("25 iterations");
    }

    private PollingWorkflow newWorkflow(String workflowId) {
        return client.newWorkflowStub(
                PollingWorkflow.class,
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
