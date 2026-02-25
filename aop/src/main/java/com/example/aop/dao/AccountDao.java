package com.example.aop.dao;

import com.example.aop.aspect.auth.RequiresRole;
import com.example.aop.aspect.cache.SimpleCache;
import com.example.aop.aspect.retry.Retryable;
import com.example.aop.aspect.validation.Min;
import com.example.aop.aspect.validation.NotNull;
import com.example.aop.aspect.validation.ValidateArgs;
import com.example.aop.entity.Account;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class AccountDao {

    private final AtomicInteger fetchCounter = new AtomicInteger(0);

    public List<Account> find(List<Integer> ids) {
        return new ArrayList<>();
    }

    public List<Account> findOrExceptionIfNotFound(List<Integer> ids) {
        throw new RuntimeException("not found any account " + ids);
    }

    public void add() {
    }

    @RequiresRole("ADMIN")
    public void delete(int id) {
        throw new RuntimeException("not allowed delete any account");
    }

    /**
     * Simulates an expensive lookup. First call is slow; subsequent calls
     * return instantly from cache thanks to the @SimpleCache aspect.
     */
    @ValidateArgs
    @SimpleCache
    public Account slowFindById(@NotNull @Min(1) Integer id) {
        try {
            Thread.sleep(500); // simulate expensive operation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new Account(id, "Account-" + id);
    }

    /**
     * Simulates an unreliable data source that fails the first 2 calls
     * and succeeds on the 3rd. Used to demonstrate the @Retryable aspect.
     */
    @Retryable(maxAttempts = 3, retryOn = RuntimeException.class)
    public Account fetchWithRetry(int id) {
        int attempt = fetchCounter.incrementAndGet();
        if (attempt % 3 != 0) {
            throw new RuntimeException("Transient failure on attempt " + attempt);
        }
        return new Account(id, "Fetched");
    }

    public void resetFetchCounter() {
        fetchCounter.set(0);
    }
}
