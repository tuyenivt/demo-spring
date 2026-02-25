package com.example.aop.controller;

import com.example.aop.aspect.feature.FeatureFlagsRegistry;
import com.example.aop.aspect.metrics.MetricsRegistry;
import com.example.aop.entity.Account;
import com.example.aop.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final MetricsRegistry metricsRegistry;
    private final FeatureFlagsRegistry featureFlagsRegistry;

    @GetMapping("/accounts/{id}")
    public Account getAccount(@PathVariable Integer id) {
        return accountService.getAccountById(id);
    }

    @PostMapping("/accounts")
    public Account createAccount(@RequestBody(required = false) CreateAccountRequest request) {
        var name = request != null ? request.name() : null;
        return accountService.createAccount(name);
    }

    @GetMapping("/accounts/retry/{id}")
    public Account getAccountWithRetry(@PathVariable int id) {
        return accountService.getAccountWithRetry(id);
    }

    @GetMapping("/accounts/rate-limited/{id}")
    public Account getRateLimitedAccount(@PathVariable Integer id) {
        return accountService.getRateLimitedAccount(id);
    }

    @GetMapping("/batch")
    public void processBatch(@RequestParam int factor) throws InterruptedException {
        accountService.processBatch(factor);
    }

    @GetMapping("/pricing/{amountCents}")
    public BigDecimal calculatePrice(@PathVariable int amountCents) {
        return accountService.calculatePrice(amountCents);
    }

    @GetMapping("/metrics")
    public Map<String, MetricsRegistry.MetricSnapshot> metrics() {
        return metricsRegistry.snapshot();
    }

    @GetMapping("/flags")
    public Map<String, Boolean> flags() {
        return featureFlagsRegistry.snapshot();
    }

    @PutMapping("/flags/{flag}")
    public Map<String, Boolean> setFlag(@PathVariable String flag, @RequestParam boolean enabled) {
        featureFlagsRegistry.set(flag, enabled);
        return featureFlagsRegistry.snapshot();
    }

    public record CreateAccountRequest(String name) {
    }
}
