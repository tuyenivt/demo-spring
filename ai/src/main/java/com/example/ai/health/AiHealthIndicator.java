package com.example.ai.health;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiHealthIndicator implements HealthIndicator {

    private final OllamaApi ollamaApi;

    @Override
    public Health health() {
        var builder = Health.up();

        try {
            var models = ollamaApi.listModels();
            builder.withDetail("ollama", "connected").withDetail("modelsAvailable", models.models().size());
        } catch (Exception e) {
            builder.down().withDetail("ollama", "disconnected").withDetail("error", e.getMessage());
        }

        return builder.build();
    }
}
