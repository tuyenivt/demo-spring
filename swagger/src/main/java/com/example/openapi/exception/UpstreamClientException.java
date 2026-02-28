package com.example.openapi.exception;

public class UpstreamClientException extends RuntimeException {

    public UpstreamClientException(String message) {
        super(message);
    }
}
