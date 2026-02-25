package com.example.aop.aspect.transaction;

import java.lang.annotation.*;

/**
 * Educational annotation to demonstrate transaction-style AOP behavior.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DemoTransactional {
}
