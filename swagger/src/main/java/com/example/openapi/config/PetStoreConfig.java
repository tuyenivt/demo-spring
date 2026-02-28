package com.example.openapi.config;

import com.example.openapi.petstore.api.PetApi;
import com.example.openapi.petstore.api.StoreApi;
import com.example.openapi.petstore.api.UserApi;
import feign.Client;
import feign.Feign;
import feign.Logger.Level;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.slf4j.Slf4jLogger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "app.pet-store")
public class PetStoreConfig {

    @Setter
    private String baseUrl;
    @Setter
    private String username;
    @Setter
    private String password;

    private final Client client;
    private final Level feignLoggerLevel;
    private final ErrorDecoder petStoreErrorDecoder;

    private <T> T buildClient(Class<T> apiType) {
        return Feign.builder()
                .client(client)
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .logger(new Slf4jLogger(apiType))
                .logLevel(feignLoggerLevel)
                .requestInterceptor(new BasicAuthRequestInterceptor(username, password))
                .errorDecoder(petStoreErrorDecoder)
                .target(apiType, baseUrl);
    }

    @Bean
    public PetApi getPetApi() {
        return buildClient(PetApi.class);
    }

    @Bean
    public StoreApi getStoreApi() {
        return buildClient(StoreApi.class);
    }

    @Bean
    public UserApi getUserApi() {
        return buildClient(UserApi.class);
    }
}
