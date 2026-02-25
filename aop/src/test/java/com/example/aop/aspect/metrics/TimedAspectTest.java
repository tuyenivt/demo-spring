package com.example.aop.aspect.metrics;

import com.example.aop.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TimedAspectTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private MetricsRegistry metricsRegistry;

    @Test
    void timedMethods_recordMetrics() {
        accountService.getAccountById(1);
        accountService.calculatePrice(1000);

        var snapshot = metricsRegistry.snapshot();
        assertThat(snapshot).containsKeys("account.get-by-id", "pricing.calculate", "account.slow-find");
        assertThat(snapshot.get("account.get-by-id").count()).isGreaterThanOrEqualTo(1);
    }
}
