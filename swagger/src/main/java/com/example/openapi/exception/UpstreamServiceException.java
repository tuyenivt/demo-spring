package com.example.openapi.exception;

public class UpstreamServiceException extends RuntimeException {

    public UpstreamServiceException(String message) {
        super(message);
    }
}
