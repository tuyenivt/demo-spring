package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.Reminder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class ReminderConsumer {

    @RabbitListener(id = "reminder", queues = RabbitMQConfig.REMINDER_QUEUE)
    public void handleReminder(Reminder reminder) {
        log.info("[REMINDER] Delayed reminder received at {}: {}", Instant.now(), reminder);
        log.info("[REMINDER] Sending notification to user {}: {}", reminder.getUserId(), reminder.getMessage());
    }
}
