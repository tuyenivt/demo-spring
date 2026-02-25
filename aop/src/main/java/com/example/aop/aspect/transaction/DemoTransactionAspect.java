package com.example.aop.aspect.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Order(-1)
@Component
@RequiredArgsConstructor
public class DemoTransactionAspect {

    private final FakeTransactionManager fakeTransactionManager;

    @Around("@annotation(com.example.aop.aspect.transaction.DemoTransactional)")
    public Object withTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        var txId = fakeTransactionManager.begin();
        var method = joinPoint.getSignature().toShortString();
        log.info("BEGIN TX {} for {}", txId, method);
        try {
            var result = joinPoint.proceed();
            log.info("COMMIT TX {} for {}", txId, method);
            return result;
        } catch (Throwable throwable) {
            log.warn("ROLLBACK TX {} for {} because {}", txId, method, throwable.getMessage());
            throw throwable;
        }
    }
}
