package com.example.openapi.config;

import com.example.openapi.feign.PetStoreErrorDecoder;
import feign.Client;
import feign.Logger;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public Client client() {
        return new OkHttpClient();
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public ErrorDecoder petStoreErrorDecoder() {
        return new PetStoreErrorDecoder();
    }
}
