package com.example.aop.service;

import com.example.aop.aspect.ExecutionLogging;
import com.example.aop.aspect.MonitorPerformance;
import com.example.aop.aspect.audit.Audited;
import com.example.aop.aspect.feature.FeatureEnabled;
import com.example.aop.aspect.metrics.Timed;
import com.example.aop.aspect.ratelimit.RateLimited;
import com.example.aop.aspect.validation.Max;
import com.example.aop.aspect.validation.Min;
import com.example.aop.aspect.validation.NotNull;
import com.example.aop.aspect.validation.ValidateArgs;
import com.example.aop.dao.AccountDao;
import com.example.aop.entity.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountDao accountDao;

    @ExecutionLogging
    @MonitorPerformance(thresholdMs = 500)
    public void serve(int factor) throws InterruptedException {
        Thread.sleep(factor * 1000L);
    }

    public List<Account> findAccounts(List<Integer> ids) {
        log.info("findAccounts - executing...");
        var found = accountDao.find(ids);
        log.info("findAccounts - returned result is {}", found);
        return found;
    }

    public List<Account> findAccountsOrExceptionIfNotFound(List<Integer> ids) {
        log.info("findAccountsOrExceptionIfNotFound - executing...");
        var found = accountDao.findOrExceptionIfNotFound(ids);
        log.info("findAccountsOrExceptionIfNotFound - returned result is {}", found);
        return found;
    }

    public void addAccount() {
        accountDao.add();
    }

    public void deleteAccount(int id) {
        accountDao.delete(id);
    }

    @ExecutionLogging
    @Audited(action = "CREATE", entity = "Account")
    public Account createAccount(String name) {
        return accountDao.create(name);
    }

    @ValidateArgs
    @MonitorPerformance(thresholdMs = 300)
    @Timed(name = "account.get-by-id")
    public Account getAccountById(@NotNull @Min(1) @Max(100_000) Integer id) {
        return accountDao.slowFindById(id);
    }

    @RateLimited(requestsPerSecond = 2)
    public Account getRateLimitedAccount(@NotNull @Min(1) Integer id) {
        return accountDao.slowFindById(id);
    }

    @RateLimited(requestsPerSecond = 2)
    public String rateLimitedPing() {
        return "pong";
    }

    public Account getAccountWithRetry(int id) {
        return accountDao.fetchWithRetry(id);
    }

    @FeatureEnabled("new-pricing-algorithm")
    @Timed(name = "pricing.calculate")
    public BigDecimal calculatePrice(int amountCents) {
        return BigDecimal.valueOf(amountCents).movePointLeft(2).multiply(new BigDecimal("0.90"));
    }

    /**
     * Demonstrates the Spring AOP self-invocation proxy limitation.
     * The internal call to serve() bypasses the proxy, so @ExecutionLogging
     * will NOT fire. This is because Spring AOP is proxy-based — "this.serve()"
     * calls the target object directly, not through the AOP proxy.
     * <p>
     * Workarounds: inject self, use AopContext.currentProxy(), or move the
     * method to a separate bean.
     */
    public void processBatch(int factor) throws InterruptedException {
        log.info("processBatch - calling serve() internally (aspect will NOT fire)");
        serve(factor); // self-invocation — bypasses proxy
    }
}
