package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderConsumer {

    @RabbitListener(id = "highPriorityOrder", queues = RabbitMQConfig.ORDER_HIGH_PRIORITY_QUEUE)
    public void handleHighPriorityOrder(Order order) {
        log.info("[HIGH PRIORITY] Processing urgent order: {}", order);
        processOrder(order, "HIGH");
    }

    @RabbitListener(id = "normalOrder", queues = RabbitMQConfig.ORDER_NORMAL_QUEUE)
    public void handleNormalOrder(Order order) {
        log.info("[NORMAL] Processing standard order: {}", order);
        processOrder(order, "NORMAL");
    }

    private void processOrder(Order order, String priority) {
        try {
            Thread.sleep(priority.equals("HIGH") ? 100 : 200);
            log.info("[{}] Order {} processed successfully for product: {} x{}",
                    priority, order.getId(), order.getProduct(), order.getQuantity());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
