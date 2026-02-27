package com.example.temporal.activities;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotificationActivities {

    @ActivityMethod
    void sendEmail(String customerId, String message);

    @ActivityMethod
    void sendSms(String customerId, String message);

    @ActivityMethod
    void sendPush(String customerId, String message);
}
