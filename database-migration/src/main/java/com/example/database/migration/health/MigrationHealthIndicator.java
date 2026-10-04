package com.example.database.migration.health;

import com.example.database.migration.demo.repository.MigrationStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class MigrationHealthIndicator implements HealthIndicator {

    private final MigrationStateRepository migrationStateRepository;
    private volatile boolean migrationRunning = false;
    private volatile LocalDateTime lastRunStart;
    private volatile int lastBatchSize = 0;

    @Override
    public Health health() {
        try {
            var state = migrationStateRepository.findById("Product");

            if (state.isEmpty()) {
                return Health.up()
                        .withDetail("status", "No migration run yet")
                        .build();
            }

            var migrationState = state.get();
            var timeSinceLastUpdate = Duration.between(
                    migrationState.getModifiedAt() != null ? migrationState.getModifiedAt() : migrationState.getCreatedAt(),
                    LocalDateTime.now()
            );

            return Health.up()
                    .withDetail("lastSyncTimestamp", migrationState.getLastUpdatedAt())
                    .withDetail("lastModified", migrationState.getModifiedAt())
                    .withDetail("timeSinceLastUpdate", timeSinceLastUpdate.toMinutes() + " minutes")
                    .withDetail("recordsProcessedInLastRun", lastBatchSize)
                    .withDetail("migrationRunning", migrationRunning)
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    public void setMigrationRunning(boolean running) {
        this.migrationRunning = running;
        if (running) {
            this.lastRunStart = LocalDateTime.now();
        }
    }

    public void setLastBatchSize(int size) {
        this.lastBatchSize = size;
    }
}
