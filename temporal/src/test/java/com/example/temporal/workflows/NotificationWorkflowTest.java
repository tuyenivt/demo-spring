package com.example.temporal.workflows;

import com.example.temporal.activities.NotificationActivities;
import com.example.temporal.workflows.impl.NotificationWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationWorkflowTest {

    private static final String TASK_QUEUE = "test-notification-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;
    private Worker worker;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void sendAll_allSucceed() {
        worker.registerActivitiesImplementations(new SuccessNotificationActivities());
        testEnv.start();

        NotificationWorkflow workflow = newWorkflow();
        String result = workflow.sendAll("CUST-1", "hello");

        assertThat(result).contains("email/sms/push");
    }

    @Test
    void sendAll_partialFailureFailsWorkflow() {
        worker.registerActivitiesImplementations(new FailingNotificationActivities());
        testEnv.start();

        NotificationWorkflow workflow = newWorkflow();

        assertThatThrownBy(() -> workflow.sendAll("CUST-1", "hello"))
                .isInstanceOf(WorkflowFailedException.class)
                .rootCause()
                .hasMessageContaining("sms failed");
    }

    private NotificationWorkflow newWorkflow() {
        return client.newWorkflowStub(
                NotificationWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TASK_QUEUE).build());
    }

    static class SuccessNotificationActivities implements NotificationActivities {
        @Override
        public void sendEmail(String customerId, String message) {
        }

        @Override
        public void sendSms(String customerId, String message) {
        }

        @Override
        public void sendPush(String customerId, String message) {
        }
    }

    static class FailingNotificationActivities extends SuccessNotificationActivities {
        @Override
        public void sendSms(String customerId, String message) {
            throw new RuntimeException("sms failed");
        }
    }
}
