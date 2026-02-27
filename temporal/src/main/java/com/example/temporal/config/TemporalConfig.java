package com.example.temporal.config;

import com.example.temporal.activities.impl.NotificationActivitiesImpl;
import com.example.temporal.activities.impl.OrderActivitiesImpl;
import com.example.temporal.activities.impl.PaymentActivitiesImpl;
import com.example.temporal.activities.impl.ReportActivitiesImpl;
import com.example.temporal.workflows.impl.*;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import io.temporal.worker.WorkerFactoryOptions;
import io.temporal.worker.WorkerOptions;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Temporal configuration for Spring Boot with graceful shutdown support.
 * <p>
 * GRACEFUL SHUTDOWN FLOW:
 * 1. Kubernetes sends SIGTERM to pod
 * 2. Spring Boot stops accepting new HTTP requests
 * 3. WorkerFactory.shutdown() initiates worker shutdown
 * 4. Workers stop polling NORMAL task queue for new tasks
 * 5. Workers continue polling STICKY task queue (for cached workflows) until drain timeout
 * 6. Active workflow tasks complete (up to sticky drain timeout)
 * 7. Active activity tasks complete
 * 8. Workers shutdown cleanly after all tasks finish or timeout
 * 9. Service stubs close connections
 * 10. Application exits
 * <p>
 * STICKY TASK QUEUE EXPLAINED:
 * - Temporal caches workflow state on workers for performance
 * - Cached workflows receive tasks via "sticky" task queue
 * - During shutdown, we want to complete these cached workflows
 * - setStickyTaskQueueDrainTimeout() allows time for this
 * <p>
 * This ensures:
 * - No workflows are interrupted mid-execution
 * - Cached workflows complete gracefully
 * - In-flight HTTP requests complete
 * - No data loss or corruption
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class TemporalConfig {
    private final TemporalProperties temporalProperties;

    private WorkerFactory workerFactory;
    private WorkflowServiceStubs workflowServiceStubs;

    /**
     * Creates connection to Temporal server.
     * In production, configure with proper TLS and authentication.
     */
    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        workflowServiceStubs = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(temporalProperties.getTarget()).build());
        return workflowServiceStubs;
    }

    /**
     * Creates Temporal client for starting and querying workflows.
     */
    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs, WorkflowClientOptions.newBuilder().setNamespace(temporalProperties.getNamespace()).build());
    }

    /**
     * Creates and starts workers that poll for tasks.
     * <p>
     * Workers are responsible for:
     * 1. Polling task queues for workflow tasks and activity tasks
     * 2. Executing workflow code (deterministic orchestration)
     * 3. Executing activity code (non-deterministic I/O operations)
     * <p>
     * GRACEFUL SHUTDOWN with STICKY TASK QUEUE DRAIN:
     * <p>
     * The sticky task queue drain timeout is CRITICAL for graceful shutdown:
     * <p>
     * 1. Normal Operation:
     * - Worker polls NORMAL task queue for new workflows
     * - Worker polls STICKY task queue for cached workflows
     * - Cached workflows are faster (state already in memory)
     * <p>
     * 2. During Shutdown:
     * - Worker STOPS polling NORMAL task queue (no new workflows)
     * - Worker CONTINUES polling STICKY task queue (finish cached workflows)
     * - Drains sticky queue for up to setStickyTaskQueueDrainTimeout()
     * - Cached workflows complete gracefully
     * <p>
     * 3. Why This Matters:
     * - Workflows cached on this worker can complete
     * - No workflow interruption mid-execution
     * - Better resource utilization (workflows don't need to transfer)
     * - Cleaner shutdown (no orphaned cached state)
     * <p>
     * BEST PRACTICE:
     * - Set drain timeout to expected max workflow execution time
     * - Must be > RPC long poll timeout (default 60s)
     * - For this demo: 30s is sufficient for our workflows
     */
    @Bean
    public WorkerFactory workerFactory(WorkflowClient workflowClient,
                                       OrderActivitiesImpl orderActivities,
                                       PaymentActivitiesImpl paymentActivities,
                                       ReportActivitiesImpl reportActivities,
                                       NotificationActivitiesImpl notificationActivities) {
        workerFactory = WorkerFactory.newInstance(workflowClient, WorkerFactoryOptions.newBuilder().setUsingVirtualWorkflowThreads(true).build());

        var workerOptions = WorkerOptions.newBuilder()
                .setStickyTaskQueueDrainTimeout(Duration.ofSeconds(temporalProperties.getShutdownTimeoutSeconds()))
                // Rate limiting for activities - prevents overwhelming external services
                .setMaxConcurrentActivityExecutionSize(10)  // Max concurrent activities
                .setMaxConcurrentWorkflowTaskExecutionSize(20)  // Max concurrent workflow tasks
                .build();

        // Create worker for order processing task queue
        var worker = workerFactory.newWorker(temporalProperties.getTaskQueue(), workerOptions);

        // Register workflow implementations
        // These are the orchestration logic - must be deterministic
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

        // Register activity implementations
        // These perform actual I/O operations - can be non-deterministic
        worker.registerActivitiesImplementations(orderActivities, paymentActivities, reportActivities, notificationActivities);

        // Start all workers
        workerFactory.start();
        log.info("Temporal workers started on task queue: {}", temporalProperties.getTaskQueue());
        log.info("Graceful shutdown enabled with timeout: {} seconds", temporalProperties.getShutdownTimeoutSeconds());

        return workerFactory;
    }

    /**
     * Gracefully shutdown workers when application stops.
     * <p>
     * SHUTDOWN SEQUENCE WITH STICKY TASK QUEUE DRAIN:
     * <p>
     * 1. workerFactory.shutdown() is called
     * - Workers stop polling NORMAL task queue
     * - Workers continue polling STICKY task queue
     * <p>
     * 2. Sticky task queue drains (up to setStickyTaskQueueDrainTimeout)
     * - Cached workflows on this worker complete
     * - No new workflows accepted
     * <p>
     * 3. After drain timeout OR sticky queue empty:
     * - Workers stop polling sticky task queue
     * - All workers shutdown
     * <p>
     * 4. awaitTermination() waits for workers to finish
     * - Returns true if all completed
     * - Returns false if timeout exceeded
     * <p>
     * 5. Service stubs close gRPC connections
     * <p>
     * This is called by Spring when:
     * - Application shutdown initiated (Ctrl+C)
     * - SIGTERM received (Kubernetes pod termination)
     * - Application context closed
     */
    @PreDestroy
    public void destroy() {
        log.info("Initiating graceful shutdown of Temporal workers...");

        if (workerFactory != null) {
            try {
                log.info("Shutting down workers:");
                log.info("  - Stopping polling of normal task queue (no new workflows)");
                log.info("  - Draining sticky task queue for up to {} seconds (complete cached workflows)", temporalProperties.getShutdownTimeoutSeconds());

                // Shutdown workers gracefully
                // This triggers:
                // 1. Stop polling normal task queue immediately
                // 2. Continue polling sticky task queue for drain timeout
                // 3. Allow cached workflows to complete
                workerFactory.shutdown();

                // Wait for workers to complete shutdown
                // This waits for:
                // 1. Sticky task queue to drain (up to setStickyTaskQueueDrainTimeout)
                // 2. All active tasks to complete
                // 3. Worker threads to terminate
                log.info("Waiting for worker shutdown to complete (timeout: {}s)...", temporalProperties.getShutdownTimeoutSeconds());

                workerFactory.awaitTermination(temporalProperties.getShutdownTimeoutSeconds(), TimeUnit.SECONDS);

                log.info("Temporal workers shutdown completed successfully");
            } catch (Exception e) {
                log.error("Error during worker shutdown", e);
            }
        }

        // Shutdown service stubs (close gRPC connections)
        if (workflowServiceStubs != null) {
            try {
                log.info("Closing Temporal service connections...");
                workflowServiceStubs.shutdown();

                workflowServiceStubs.awaitTermination(5, TimeUnit.SECONDS);

                log.info("Temporal service connections closed successfully");
            } catch (Exception e) {
                log.error("Error closing service stubs", e);
            }
        }

        log.info("Temporal graceful shutdown complete");
    }
}
