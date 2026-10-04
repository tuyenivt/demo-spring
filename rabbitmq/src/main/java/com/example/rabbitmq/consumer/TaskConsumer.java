package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.Task;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TaskConsumer {

    @RabbitListener(id = "task", queues = RabbitMQConfig.TASK_QUEUE, ackMode = "MANUAL")
    public void processTask(Task task, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("[WORKER] Received task: {}", task);
        try {
            // Simulate CPU-intensive work
            simulateWork(task);
            channel.basicAck(deliveryTag, false);
            log.info("[WORKER] Task completed successfully: {}", task.getId());
        } catch (Exception e) {
            log.error("[WORKER] Task failed, requeuing: {}", task.getId(), e);
            channel.basicNack(deliveryTag, false, true); // Requeue on failure
        }
    }

    private void simulateWork(Task task) throws InterruptedException {
        log.info("[WORKER] Processing task '{}' with priority {}", task.getName(), task.getPriority());
        Thread.sleep(500); // Simulate work
    }
}
