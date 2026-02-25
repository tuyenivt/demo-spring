package com.example.aop.aspect.transaction;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class FakeTransactionManager {

    private final AtomicLong counter = new AtomicLong();

    public String begin() {
        return "tx-" + counter.incrementAndGet();
    }
}
