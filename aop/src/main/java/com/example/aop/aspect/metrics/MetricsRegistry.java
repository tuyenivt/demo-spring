package com.example.aop.aspect.metrics;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MetricsRegistry {

    private final ConcurrentHashMap<String, LongSummaryStatistics> metrics = new ConcurrentHashMap<>();

    public void record(String name, long durationMs) {
        metrics.compute(name, (key, stats) -> {
            var target = stats == null ? new LongSummaryStatistics() : stats;
            synchronized (target) {
                target.accept(durationMs);
            }
            return target;
        });
    }

    public Map<String, MetricSnapshot> snapshot() {
        var result = new LinkedHashMap<String, MetricSnapshot>();
        metrics.forEach((name, stats) -> {
            synchronized (stats) {
                result.put(name, new MetricSnapshot(
                        stats.getCount(),
                        stats.getMin(),
                        stats.getMax(),
                        stats.getAverage()
                ));
            }
        });
        return result;
    }

    public record MetricSnapshot(long count, long minMs, long maxMs, double avgMs) {
    }
}
