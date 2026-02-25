package com.example.aop.aspect.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "aop.feature-flags.aspect-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class FeatureFlagAspect {

    private final FeatureFlagsRegistry featureFlagsRegistry;

    @Around("@annotation(featureEnabled)")
    public Object guardFeature(ProceedingJoinPoint joinPoint, FeatureEnabled featureEnabled) throws Throwable {
        var flag = featureEnabled.value();
        if (!featureFlagsRegistry.isEnabled(flag)) {
            log.warn("FEATURE DISABLED: {} for {}", flag, joinPoint.getSignature().toShortString());
            throw new FeatureDisabledException("Feature '" + flag + "' is disabled");
        }
        return joinPoint.proceed();
    }
}
