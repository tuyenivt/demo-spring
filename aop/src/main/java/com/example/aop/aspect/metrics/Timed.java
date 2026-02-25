package com.example.aop.aspect.metrics;

import java.lang.annotation.*;

/**
 * Records method duration in the in-memory MetricsRegistry.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {
    String name();
}
