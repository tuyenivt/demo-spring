package com.example.aop.aspect.validation;

import java.lang.annotation.*;

/**
 * Triggers lightweight parameter validation based on custom annotations.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidateArgs {
}
