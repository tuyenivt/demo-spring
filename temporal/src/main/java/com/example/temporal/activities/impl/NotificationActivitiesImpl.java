package com.example.temporal.activities.impl;

import com.example.temporal.activities.NotificationActivities;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationActivitiesImpl implements NotificationActivities {

    @Override
    public void sendEmail(String customerId, String message) {
        log.info("Email sent: customerId={}, message={}", customerId, message);
    }

    @Override
    public void sendSms(String customerId, String message) {
        log.info("SMS sent: customerId={}, message={}", customerId, message);
    }

    @Override
    public void sendPush(String customerId, String message) {
        log.info("Push notification sent: customerId={}, message={}", customerId, message);
    }
}
