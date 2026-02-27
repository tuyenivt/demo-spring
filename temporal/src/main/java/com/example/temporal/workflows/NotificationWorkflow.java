package com.example.temporal.workflows;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface NotificationWorkflow {

    @WorkflowMethod
    String sendAll(String customerId, String message);
}
