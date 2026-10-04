package com.example.monitor.metrics;

import com.example.monitor.repository.CustomerRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomerMetrics implements MeterBinder {

    private final CustomerRepository repository;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("customer.count", repository::count)
                .description("Total number of customers in the database")
                .register(registry);
    }
}
