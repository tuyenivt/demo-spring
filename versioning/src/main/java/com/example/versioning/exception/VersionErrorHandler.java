package com.example.versioning.exception;

import com.example.versioning.dto.ApiVersionError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

@ControllerAdvice
public class VersionErrorHandler {

    private final List<String> supportedVersions;
    private final String currentVersion;

    public VersionErrorHandler(@Value("${spring.mvc.apiversion.supported}") List<String> supportedVersions,
                               @Value("${spring.mvc.apiversion.default}") String currentVersion) {
        this.supportedVersions = supportedVersions;
        this.currentVersion = currentVersion;
    }

    /**
     * Unparsable versions and versions outside {@code spring.mvc.apiversion.supported}.
     */
    @ExceptionHandler(InvalidApiVersionException.class)
    public ResponseEntity<ApiVersionError> handleInvalidApiVersion(InvalidApiVersionException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiVersionError(
                "Unsupported API version",
                exception.getVersion(),
                supportedVersions,
                currentVersion,
                "/api/versions"
        ));
    }
}
