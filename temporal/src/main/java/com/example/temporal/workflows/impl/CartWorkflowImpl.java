package com.example.temporal.workflows.impl;

import com.example.temporal.workflows.CartWorkflow;
import io.temporal.workflow.Workflow;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CartWorkflowImpl implements CartWorkflow {

    private static final Duration CART_IDLE_TIMEOUT = Duration.ofMinutes(30);

    private final List<String> items = new ArrayList<>();
    private long lastUpdateEpochMillis = 0;

    @Override
    public void startCart(String cartId) {
        if (lastUpdateEpochMillis == 0) {
            lastUpdateEpochMillis = Workflow.currentTimeMillis();
        }

        while (true) {
            var observed = lastUpdateEpochMillis;
            var hasNewItem = Workflow.await(CART_IDLE_TIMEOUT, () -> lastUpdateEpochMillis > observed);
            if (!hasNewItem) {
                log.info("Cart workflow closed due to inactivity: cartId={}, items={}", cartId, items.size());
                return;
            }
        }
    }

    @Override
    public void addItem(String item) {
        items.add(item);
        lastUpdateEpochMillis = Workflow.currentTimeMillis();
        log.info("Item added to cart: item={}, totalItems={}", item, items.size());
    }

    @Override
    public List<String> getItems() {
        return List.copyOf(items);
    }
}
