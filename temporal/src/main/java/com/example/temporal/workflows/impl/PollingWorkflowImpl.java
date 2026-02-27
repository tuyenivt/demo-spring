package com.example.temporal.workflows.impl;

import com.example.temporal.activities.OrderActivities;
import com.example.temporal.workflows.PollingWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Continue-As-New polling workflow implementation.
 * <p>
 * CONTINUE-AS-NEW EXPLAINED:
 * <p>
 * Each call to {@code Workflow.continueAsNew()} does NOT call the workflow method recursively.
 * Instead, it:
 * 1. Schedules a new workflow run with the same workflow ID
 * 2. Throws {@code ContinueAsNewException} to terminate the CURRENT run cleanly
 * 3. The new run starts fresh with an empty event history
 * 4. Arguments passed to continueAsNew() become the arguments of the new run
 * <p>
 * State transfer:
 * - {@code iterationCount} is passed as an argument and incremented each run
 * - Only pass lightweight state — not the full history of what happened
 * <p>
 * History reset threshold:
 * - This demo resets every {@code HISTORY_RESET_THRESHOLD} iterations
 * - In production, check {@code Workflow.getInfo().isContinueAsNewSuggested()}
 * to let Temporal decide when history is large enough
 */
@Slf4j
public class PollingWorkflowImpl implements PollingWorkflow {

    /**
     * Reset history after this many iterations per run.
     */
    private static final int HISTORY_RESET_THRESHOLD = 10;

    /**
     * Stop polling after this many total iterations (across all continue-as-new runs).
     */
    private static final int MAX_TOTAL_ITERATIONS = 50;

    /**
     * Interval between polls.
     */
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .build())
            .build();

    private final OrderActivities activities = Workflow.newActivityStub(OrderActivities.class, activityOptions);

    // Mutable state within this run (reset on continueAsNew)
    private int currentRunIterations = 0;
    private int totalIterations; // carried across continueAsNew via arguments

    @Override
    public String startPolling(String targetId, int iterationCount) {
        this.totalIterations = iterationCount;
        log.info("Polling workflow run started: targetId={}, totalIterations={}", targetId, totalIterations);

        while (totalIterations < MAX_TOTAL_ITERATIONS) {
            totalIterations++;
            currentRunIterations++;

            log.info("Polling iteration {}/{}: targetId={}", totalIterations, MAX_TOTAL_ITERATIONS, targetId);

            // Simulate a poll — in production: call an external API, check a queue, etc.
            // Using sendNotification as a proxy for "do some external check"
            activities.sendNotification("system", "Poll #" + totalIterations + " for " + targetId);

            // Check if we've found what we're looking for (demo: stop at a round number)
            if (totalIterations % 25 == 0) {
                log.info("Polling target reached at iteration {}", totalIterations);
                return "Polling completed after " + totalIterations + " iterations for " + targetId;
            }

            // CONTINUE-AS-NEW: reset history to prevent unbounded growth.
            // This run has processed HISTORY_RESET_THRESHOLD iterations.
            // Start a fresh run, passing the accumulated totalIterations count.
            if (currentRunIterations >= HISTORY_RESET_THRESHOLD
                    || Workflow.getInfo().isContinueAsNewSuggested()) {
                log.info("Resetting event history via continueAsNew after {} iterations in this run " +
                        "(total: {})", currentRunIterations, totalIterations);

                // continueAsNew throws ContinueAsNewException — execution of this run ends here.
                // The new run will start with startPolling(targetId, totalIterations).
                Workflow.continueAsNew(targetId, totalIterations);
            }

            // Durable sleep between polls — survives worker restarts
            Workflow.sleep(POLL_INTERVAL);
        }

        return "Polling stopped after reaching max iterations: " + MAX_TOTAL_ITERATIONS;
    }

    @Override
    public int getIterationCount() {
        return totalIterations;
    }
}
