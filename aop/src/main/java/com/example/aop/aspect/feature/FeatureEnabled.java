package com.example.aop.aspect.feature;

import java.lang.annotation.*;

/**
 * Guards method execution behind a named runtime feature flag.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FeatureEnabled {
    String value();
}
