package com.example.versioning.config;

import com.example.versioning.metrics.VersionUsageMetrics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.accept.ApiVersionHolder;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Records per-version request metrics. The version itself is resolved by Spring MVC's
 * API versioning and read from the request attribute it populates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiVersionInterceptor implements HandlerInterceptor {

    public static final String REQUEST_START_NANOS = "requestStartNanos";
    public static final String UNVERSIONED = "unversioned";

    private final VersionUsageMetrics versionUsageMetrics;

    /**
     * Returns the resolved API version as {@code v<major>}, or {@value #UNVERSIONED} when the request
     * was not handled by a versioned handler mapping.
     */
    public static String currentVersion(HttpServletRequest request) {
        if (request.getAttribute(HandlerMapping.API_VERSION_ATTRIBUTE) instanceof ApiVersionHolder holder
                && holder.getVersionIfPresent() instanceof SemanticApiVersionParser.Version version) {
            return "v" + version.getMajor();
        }
        return UNVERSIONED;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(REQUEST_START_NANOS, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        var version = currentVersion(request);
        var start = request.getAttribute(REQUEST_START_NANOS);
        var elapsedMillis = 0L;
        if (start instanceof Long startNanos) {
            elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        }

        versionUsageMetrics.record(version, request.getMethod(), String.valueOf(response.getStatus()));
        log.info(
                "apiVersionRequest method={} uri={} version={} status={} durationMs={}",
                request.getMethod(),
                request.getRequestURI(),
                version,
                response.getStatus(),
                elapsedMillis
        );
    }
}
