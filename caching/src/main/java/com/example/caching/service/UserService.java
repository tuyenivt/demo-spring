package com.example.caching.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final StringRedisTemplate redisTemplate;

    public void addUserActivity(String userId, String activity) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        if (activity == null || activity.isBlank()) {
            throw new IllegalArgumentException("activity cannot be null or blank");
        }
        var key = "user:activities:" + userId;
        redisTemplate.opsForList().rightPush(key, activity);
        redisTemplate.opsForList().trim(key, -100, -1);
        redisTemplate.expire(key, Duration.ofDays(7));
    }

    public String getOldestUserActivity(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        return redisTemplate.opsForList().leftPop("user:activities:" + userId);
    }

    public List<String> getUserActivities(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId cannot be null or blank");
        }
        var activities = redisTemplate.opsForList().range("user:activities:" + userId, 0, -1);
        return activities == null ? List.of() : activities;
    }
}
