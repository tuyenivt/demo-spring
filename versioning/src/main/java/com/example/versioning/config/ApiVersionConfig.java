package com.example.versioning.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionDeprecationHandler;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;

import java.net.URI;
import java.time.ZonedDateTime;

/**
 * Native API versioning beans picked up by Spring Boot's MVC auto-configuration,
 * on top of the resolvers configured via {@code spring.mvc.apiversion.*}.
 */
@Configuration
public class ApiVersionConfig {

    public static final ZonedDateTime V1_DEPRECATION_DATE = ZonedDateTime.parse("2025-01-01T00:00:00Z");
    public static final ZonedDateTime V1_SUNSET_DATE = ZonedDateTime.parse("2025-12-31T23:59:59Z");

    /**
     * URI path versioning for {@code /v1/**}, {@code /v2/**} and {@code /api/v2/**}.
     * Runs after the header, query parameter and media type resolvers; other paths fall back to the default version.
     */
    @Bean
    public ApiVersionResolver pathPrefixApiVersionResolver() {
        return request -> {
            var path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/v1/")) {
                return "1";
            }
            if (path.startsWith("/v2/") || path.startsWith("/api/v2/")) {
                return "2";
            }
            return null;
        };
    }

    /**
     * Adds RFC 9745 {@code Deprecation}, RFC 8594 {@code Sunset} and {@code Link} headers to every v1 response.
     */
    @Bean
    @ConditionalOnBooleanProperty(name = "api.v1.enabled", matchIfMissing = true)
    public ApiVersionDeprecationHandler apiVersionDeprecationHandler() {
        var handler = new StandardApiVersionDeprecationHandler();
        handler.configureVersion("1")
                .setDeprecationDate(V1_DEPRECATION_DATE)
                .setDeprecationLink(URI.create("/api/versions"))
                .setSunsetDate(V1_SUNSET_DATE);
        return handler;
    }
}
