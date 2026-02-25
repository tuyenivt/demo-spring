package com.example.aop.aspect.ratelimit;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {
    int requestsPerSecond();
}
