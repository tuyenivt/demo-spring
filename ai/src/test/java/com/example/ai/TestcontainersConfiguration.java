package com.example.ai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.qdrant.QdrantContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    private static final String CHAT_MODEL = "llama3.2:1b";
    private static final String EMBEDDING_MODEL = "mxbai-embed-large";

    private static final OllamaContainer OLLAMA;

    static {
        OLLAMA = new OllamaContainer(DockerImageName.parse("ollama/ollama:0.35.1"))
                .withStartupTimeout(Duration.ofMinutes(5));
        OLLAMA.start();

        try {
            OLLAMA.execInContainer("ollama", "pull", CHAT_MODEL);
            OLLAMA.execInContainer("ollama", "pull", EMBEDDING_MODEL);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to pull models", e);
        }
    }

    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() {
        return OLLAMA;
    }

    @Bean
    @ServiceConnection
    QdrantContainer qdrantContainer() {
        return new QdrantContainer(DockerImageName.parse("qdrant/qdrant:v1.19"));
    }
}
