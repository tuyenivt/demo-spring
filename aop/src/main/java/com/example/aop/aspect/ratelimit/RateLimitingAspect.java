package com.example.aop.aspect.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Aspect
@Component
public class RateLimitingAspect {

    private final ConcurrentHashMap<String, FixedWindowRateLimiter> limiters = new ConcurrentHashMap<>();

    @Before("@annotation(rateLimited)")
    public void checkRate(JoinPoint joinPoint, RateLimited rateLimited) {
        var method = joinPoint.getSignature().toLongString();
        var caller = Thread.currentThread().getName();
        var limiterKey = method + "|" + caller;

        var limiter = limiters.compute(limiterKey, (key, existing) -> {
            if (existing == null || existing.limit() != rateLimited.requestsPerSecond()) {
                return new FixedWindowRateLimiter(rateLimited.requestsPerSecond());
            }
            return existing;
        });

        if (!limiter.tryAcquire()) {
            log.warn("RATE LIMITED: {} for caller {}", joinPoint.getSignature().toShortString(), caller);
            throw new RateLimitExceededException("Rate limit exceeded for " + joinPoint.getSignature().toShortString());
        }
    }

    private static final class FixedWindowRateLimiter {
        private final int limit;
        private long windowStartMs;
        private int used;

        private FixedWindowRateLimiter(int limit) {
            this.limit = limit;
            this.windowStartMs = System.currentTimeMillis();
            this.used = 0;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now - windowStartMs >= 1000) {
                windowStartMs = now;
                used = 0;
            }
            if (used >= limit) {
                return false;
            }
            used++;
            return true;
        }

        int limit() {
            return limit;
        }
    }
}
