package com.example.idempotent.idempotent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class IdempotentAspect {

    private final IdempotentConfig config;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String FIRST_REQUEST = "first-request";

    @Pointcut("@annotation(com.example.idempotent.idempotent.PreventRepeatedRequests)")
    private void forAnnotationPreventRepeatedRequests() {
        // Pointcut declaration
    }

    @Pointcut("@annotation(com.example.idempotent.idempotent.Idempotent)")
    private void forAnnotationIdempotent() {
        // Pointcut declaration
    }

    @Before("forAnnotationPreventRepeatedRequests() && @annotation(preventRepeatedRequests)")
    public void preventRepeatedRequests(JoinPoint jp, PreventRepeatedRequests preventRepeatedRequests) {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        Assert.notNull(attributes, "Attributes can not null");
        var request = attributes.getRequest();

        var clientIdempotentKey = request.getHeader(config.getClientHeaderKey());
        var isReplay = Boolean.parseBoolean(request.getHeader(config.getClientHeaderReplay()));

        log.info("IdempotentAspect idempotent call with {} = {}, {} = {}", config.getClientHeaderKey(), clientIdempotentKey, config.getClientHeaderReplay(), isReplay);
        var cacheName = String.join("_", config.getCacheStoreKey(), jp.getSignature().getName(),
                StringUtils.hasText(clientIdempotentKey) ? clientIdempotentKey : Arrays.toString(jp.getArgs()));
        log.debug("IdempotentAspect idempotent checking cacheName = {}", cacheName);

        // Use annotation attributes or fall back to global config
        var timeout = preventRepeatedRequests.timeout() >= 0 ? preventRepeatedRequests.timeout() : config.getTimeoutMinutes();
        var timeUnit = preventRepeatedRequests.timeUnit();

        if (isReplay) {
            // Force replay: delete existing key and set new lock
            redisTemplate.delete(cacheName);
            redisTemplate.opsForValue().set(cacheName, FIRST_REQUEST, timeout, timeUnit);
        } else {
            // Atomic check-and-set: only one request wins
            var acquired = redisTemplate.opsForValue().setIfAbsent(cacheName, FIRST_REQUEST, timeout, timeUnit);
            if (Boolean.FALSE.equals(acquired)) {
                throw new IdempotentException("Repeated requests, previous request expired in " + timeout + " " + timeUnit.name().toLowerCase());
            }
        }
    }

    @Around("forAnnotationIdempotent() && @annotation(idempotent)")
    public Object idempotent(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        Assert.notNull(attributes, "Attributes can not null");
        var request = attributes.getRequest();

        var ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (!StringUtils.hasText(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        var path = request.getServletPath();
        var clientHeaderKeyVal = request.getHeader(config.getClientHeaderKey());

        // Require idempotent key header to prevent fragile cache keys
        if (!StringUtils.hasText(clientHeaderKeyVal)) {
            throw new IllegalArgumentException("Missing required header: " + config.getClientHeaderKey());
        }

        var cacheKey = String.join("_", config.getCacheStoreKey(), ipAddress, path, clientHeaderKeyVal);
        log.debug("cacheKey: {}", cacheKey);

        var replay = Boolean.parseBoolean(request.getHeader(config.getClientHeaderReplay()));

        // Use annotation attributes or fall back to global config
        var timeout = idempotent.timeout() >= 0 ? idempotent.timeout() : config.getTimeoutMinutes();
        var timeUnit = idempotent.timeUnit();
        var resultExpire = idempotent.resultExpire() >= 0 ? idempotent.resultExpire() : config.getResultExpireMinutes();

        if (replay) {
            // Force replay: delete existing key and proceed
            redisTemplate.delete(cacheKey);
        } else {
            // Check for existing cached result
            var keyVal = redisTemplate.opsForValue().get(cacheKey);
            log.debug("keyVal: {}", keyVal);
            if (Objects.nonNull(keyVal)) {
                if (FIRST_REQUEST.equalsIgnoreCase(String.valueOf(keyVal))) {
                    throw new IdempotentException(String.join(" ",
                            "Repeated submissions, previous request expired in",
                            String.valueOf(redisTemplate.getExpire(cacheKey, timeUnit)),
                            timeUnit.name().toLowerCase() + ",",
                            "store result in 24h, please retry to get result if it's ready"));
                }
                if (keyVal instanceof CachedResponse cachedResponse) {
                    return ResponseEntity.status(cachedResponse.getStatusCode()).body(cachedResponse.getBody());
                }
                // Backward compatibility for old cached body-only values.
                return ResponseEntity.ok(keyVal);
            }
        }

        // Atomic check-and-set: only one request wins the lock
        var acquired = redisTemplate.opsForValue().setIfAbsent(cacheKey, FIRST_REQUEST, timeout, timeUnit);
        if (Boolean.FALSE.equals(acquired)) {
            throw new IdempotentException(String.join(" ",
                    "Repeated submissions, previous request expired in",
                    String.valueOf(redisTemplate.getExpire(cacheKey, timeUnit)),
                    timeUnit.name().toLowerCase() + ",",
                    "please retry to get result if it's ready"));
        }

        try {
            var result = joinPoint.proceed();
            log.debug("result: {}", result);
            var toCache = result;
            if (result instanceof ResponseEntity<?> re) {
                toCache = new CachedResponse(re.getStatusCode().value(), re.getBody());
            }
            redisTemplate.opsForValue().set(cacheKey, toCache, resultExpire, TimeUnit.MINUTES);
            return result;
        } catch (Throwable ex) {
            // Delete key on failure to allow retries
            log.warn("Method execution failed, cleaning up cache key: {}", cacheKey);
            redisTemplate.delete(cacheKey);
            throw ex;
        }
    }
}
