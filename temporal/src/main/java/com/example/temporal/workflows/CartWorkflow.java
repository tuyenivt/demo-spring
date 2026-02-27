package com.example.temporal.workflows;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

@WorkflowInterface
public interface CartWorkflow {

    @WorkflowMethod
    void startCart(String cartId);

    @SignalMethod
    void addItem(String item);

    @QueryMethod
    List<String> getItems();
}
