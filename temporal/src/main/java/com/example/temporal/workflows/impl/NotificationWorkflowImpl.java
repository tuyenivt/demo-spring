package com.example.temporal.workflows.impl;

import com.example.temporal.activities.NotificationActivities;
import com.example.temporal.workflows.NotificationWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class NotificationWorkflowImpl implements NotificationWorkflow {

    private final ActivityOptions activityOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(2)
                    .build())
            .build();

    private final NotificationActivities activities = Workflow.newActivityStub(NotificationActivities.class, activityOptions);

    @Override
    public String sendAll(String customerId, String message) {
        var emailPromise = Async.procedure(activities::sendEmail, customerId, message);
        var smsPromise = Async.procedure(activities::sendSms, customerId, message);
        var pushPromise = Async.procedure(activities::sendPush, customerId, message);

        Promise.allOf(emailPromise, smsPromise, pushPromise).get();
        log.info("Fan-out notification completed: customerId={}", customerId);
        return "Notifications sent via email/sms/push";
    }
}
