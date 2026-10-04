package com.example.openapi.config;

import com.example.openapi.petstore.api.PetApi;
import com.example.openapi.petstore.api.StoreApi;
import com.example.openapi.petstore.api.UserApi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Client;
import feign.Feign;
import feign.Logger.Level;
import feign.RequestInterceptor;
import feign.Retryer;
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
    private final Retryer retryer;
    private final RequestInterceptor correlationIdInterceptor;
    private final ErrorDecoder petStoreErrorDecoder;

    // The generated client models use Jackson 2 annotations and java.time (OffsetDateTime) fields
    private static final ObjectMapper PET_STORE_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private <T> T buildClient(Class<T> apiType) {
        return Feign.builder()
                .client(client)
                .encoder(new JacksonEncoder(PET_STORE_MAPPER))
                .decoder(new JacksonDecoder(PET_STORE_MAPPER))
                .logger(new Slf4jLogger(apiType))
                .logLevel(feignLoggerLevel)
                .retryer(retryer)
                .requestInterceptor(new BasicAuthRequestInterceptor(username, password))
                .requestInterceptor(correlationIdInterceptor)
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
