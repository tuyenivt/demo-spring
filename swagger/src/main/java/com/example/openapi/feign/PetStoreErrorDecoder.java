package com.example.openapi.feign;

import com.example.openapi.exception.PetNotFoundException;
import com.example.openapi.exception.UpstreamClientException;
import com.example.openapi.exception.UpstreamServiceException;
import feign.Response;
import feign.codec.DefaultErrorDecoder;
import feign.codec.ErrorDecoder;

public class PetStoreErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new DefaultErrorDecoder();

    @Override
    public Exception decode(String methodKey, Response response) {
        var status = response.status();
        if (status == 404) {
            return new PetNotFoundException("Resource not found in upstream API");
        }
        if (status >= 400 && status < 500) {
            return new UpstreamClientException("Upstream client error: " + status);
        }
        if (status >= 500) {
            return new UpstreamServiceException("Upstream service error: " + status);
        }
        return defaultDecoder.decode(methodKey, response);
    }
}
