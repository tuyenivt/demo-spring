package com.example.temporal.workflows;

import com.example.temporal.activities.ReportActivities;
import com.example.temporal.workflows.impl.ReportWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWorkflowTest {

    private static final String TASK_QUEUE = "test-report-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;
    private TrackingReportActivities trackingReportActivities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ReportWorkflowImpl.class);
        trackingReportActivities = new TrackingReportActivities();
        worker.registerActivitiesImplementations(trackingReportActivities);
        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void firstRun_noPreviousResult() {
        ReportWorkflow workflow = newWorkflow("daily-report-first", null);
        String result = workflow.generateDailyReport();

        assertThat(result).contains("previous=none");
        assertThat(trackingReportActivities.inputs.getFirst()).doesNotContain("|prev=");
    }

    @Test
    void secondRun_readsLastCompletionResult() {
        ReportWorkflow workflow = newWorkflow("daily-report-cron", "* * * * *");
        WorkflowClient.start(workflow::generateDailyReport);

        testEnv.sleep(Duration.ofMinutes(3));

        assertThat(trackingReportActivities.inputs.size()).isGreaterThanOrEqualTo(2);
        assertThat(trackingReportActivities.inputs.get(1)).contains("|prev=");
    }

    private ReportWorkflow newWorkflow(String workflowId, String cron) {
        WorkflowOptions.Builder builder = WorkflowOptions.newBuilder()
                .setTaskQueue(TASK_QUEUE)
                .setWorkflowId(workflowId)
                .setWorkflowExecutionTimeout(Duration.ofMinutes(5));

        if (cron != null) {
            builder.setCronSchedule(cron);
        }

        return client.newWorkflowStub(ReportWorkflow.class, builder.build());
    }

    static class TrackingReportActivities implements ReportActivities {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final List<String> inputs = new ArrayList<>();

        @Override
        public synchronized String generateOrderReport(String reportDate) {
            inputs.add(reportDate);
            return "report-" + counter.incrementAndGet() + "-" + reportDate;
        }
    }
}
