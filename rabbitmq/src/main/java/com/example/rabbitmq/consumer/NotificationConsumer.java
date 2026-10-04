package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationConsumer {

    @RabbitListener(id = "emailNotification", queues = RabbitMQConfig.NOTIFICATION_EMAIL_QUEUE)
    public void handleEmailNotification(Notification notification) {
        log.info("[EMAIL] Sending email notification to {}: {}", notification.getRecipient(), notification.getMessage());
        // Simulate email sending
        simulateProcessing("email");
    }

    @RabbitListener(id = "smsNotification", queues = RabbitMQConfig.NOTIFICATION_SMS_QUEUE)
    public void handleSmsNotification(Notification notification) {
        log.info("[SMS] Sending SMS notification to {}: {}", notification.getRecipient(), notification.getMessage());
        // Simulate SMS sending
        simulateProcessing("sms");
    }

    private void simulateProcessing(String type) {
        try {
            Thread.sleep(100);
            log.info("[{}] Notification sent successfully", type.toUpperCase());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
