package com.example.aop.aspect.validation;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ValidationAspect {

    @Before("@annotation(com.example.aop.aspect.validation.ValidateArgs)")
    public void validate(JoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var parameters = signature.getMethod().getParameters();
        var args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            var parameter = parameters[i];
            var value = args[i];
            var paramName = parameter.getName();

            if (parameter.isAnnotationPresent(NotNull.class) && value == null) {
                throw new IllegalArgumentException("Parameter '" + paramName + "' must not be null");
            }

            var min = parameter.getAnnotation(Min.class);
            if (min != null && value != null) {
                long numeric = toLong(value, paramName, "@Min");
                if (numeric < min.value()) {
                    throw new IllegalArgumentException("Parameter '" + paramName + "' must be >= " + min.value());
                }
            }

            var max = parameter.getAnnotation(Max.class);
            if (max != null && value != null) {
                long numeric = toLong(value, paramName, "@Max");
                if (numeric > max.value()) {
                    throw new IllegalArgumentException("Parameter '" + paramName + "' must be <= " + max.value());
                }
            }
        }
    }

    private long toLong(Object value, String paramName, String annotationName) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalArgumentException("Parameter '" + paramName + "' must be numeric for " + annotationName);
    }
}
