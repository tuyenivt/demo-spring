package com.example.temporal.workflows.impl;

import com.example.temporal.activities.ReportActivities;
import com.example.temporal.workflows.ReportWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Daily report cron workflow implementation.
 * <p>
 * CRON WORKFLOW BEST PRACTICES:
 * <p>
 * 1. {@code setCronSchedule()} on WorkflowOptions — Temporal schedules each run automatically
 * 2. {@code Workflow.getLastCompletionResult()} — read the previous run's result for incremental processing
 * 3. {@code setWorkflowExecutionTimeout()} — prevents a single run from running indefinitely
 * 4. Only one workflow with the same ID runs at a time (natural dedup)
 * 5. Cron workflows retry on next scheduled run, not immediately — don't set aggressive retry policies
 * <p>
 * Difference from regular workflows:
 * - A regular workflow runs once and completes
 * - A cron workflow auto-restarts according to the schedule
 * - Each run gets fresh event history (no history growth problem)
 */
@Slf4j
public class ReportWorkflowImpl implements ReportWorkflow {

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .build())
            .build();

    private final ReportActivities reportActivities = Workflow.newActivityStub(ReportActivities.class, activityOptions);

    @Override
    public String generateDailyReport() {
        // Use Workflow.currentTimeMillis() — NOT System.currentTimeMillis()
        // This ensures deterministic replay (same time during replay as original execution)
        var reportDate = LocalDate.ofInstant(Instant.ofEpochMilli(Workflow.currentTimeMillis()), ZoneOffset.UTC).toString();

        log.info("Running daily report cron workflow for date: {}", reportDate);

        // Check if there was a previous successful run result
        // getLastCompletionResult() returns the result of the last completed run
        // Useful for incremental processing: "pick up where last run left off"
        var lastResult = Workflow.getLastCompletionResult(String.class);
        if (lastResult != null) {
            log.info("Previous run result: {}", lastResult);
        } else {
            log.info("First cron run (no previous result)");
        }

        var activityInput = lastResult == null ? reportDate : reportDate + "|prev=" + lastResult;
        var report = reportActivities.generateOrderReport(activityInput);
        var decoratedReport = "current=" + report + ", previous=" + (lastResult == null ? "none" : lastResult);

        log.info("Daily report completed: {}", decoratedReport);
        return decoratedReport;
    }
}
