package com.example.rabbitmq.consumer;

import com.example.rabbitmq.config.RabbitMQConfig;
import com.example.rabbitmq.dto.Payment;
import com.example.rabbitmq.exception.PaymentProcessingException;
import com.example.rabbitmq.exception.PaymentValidationException;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;

@Slf4j
@Component
public class PaymentConsumer {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("10000");

    @RabbitListener(id = "payment", queues = RabbitMQConfig.PAYMENT_QUEUE, ackMode = "MANUAL")
    public void processPayment(Payment payment, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("[PAYMENT] Processing payment: {}", payment);

        try {
            validatePayment(payment);
            processPaymentTransaction(payment);
            channel.basicAck(deliveryTag, false);
            log.info("[PAYMENT] Payment {} processed successfully", payment.getId());
        } catch (PaymentValidationException e) {
            // Unrecoverable: validation failed, send to DLQ
            log.error("[PAYMENT] Validation failed for payment {}: {}", payment.getId(), e.getMessage());
            channel.basicReject(deliveryTag, false); // false = don't requeue, goes to DLQ
        } catch (PaymentProcessingException e) {
            // Recoverable: processing error, requeue for retry
            log.warn("[PAYMENT] Processing failed for payment {}, requeuing: {}", payment.getId(), e.getMessage());
            channel.basicNack(deliveryTag, false, true); // true = requeue
        }
    }

    private void validatePayment(Payment payment) throws PaymentValidationException {
        if (payment.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new PaymentValidationException("Amount exceeds maximum limit of " + MAX_AMOUNT);
        }
        if (payment.getCustomerId() == null || payment.getCustomerId().isBlank()) {
            throw new PaymentValidationException("Customer ID is required");
        }
    }

    private void processPaymentTransaction(Payment payment) throws PaymentProcessingException {
        // Simulate payment gateway call that could fail temporarily
        log.info("[PAYMENT] Calling payment gateway for customer {}", payment.getCustomerId());
    }

    @RabbitListener(id = "failedPayment", queues = RabbitMQConfig.PAYMENT_DLQ)
    public void handleFailedPayment(Payment payment) {
        log.warn("[DLQ] Handling failed payment: {}", payment);
        // Actions for unrecoverable failures:
        // - Store in database for manual review
        // - Send alert to operations team
        // - Create support ticket
        log.info("[DLQ] Failed payment {} logged for manual review", payment.getId());
    }
}
