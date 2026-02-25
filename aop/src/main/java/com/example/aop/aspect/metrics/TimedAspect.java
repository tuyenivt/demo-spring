package com.example.aop.aspect.metrics;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Order(4)
@Component
@RequiredArgsConstructor
public class TimedAspect {

    private final MetricsRegistry metricsRegistry;

    @Around("@annotation(timed)")
    public Object collectTiming(ProceedingJoinPoint joinPoint, Timed timed) throws Throwable {
        var start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            var durationMs = (System.nanoTime() - start) / 1_000_000;
            metricsRegistry.record(timed.name(), durationMs);
        }
    }
}
