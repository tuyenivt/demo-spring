package com.example.modulith;

import com.example.modulith.customer.CustomerFacade;
import com.example.modulith.customer.RegisterCustomerCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Runs the Flyway migrations against real MySQL and lets Hibernate validate the schema,
 * including the Spring Modulith event publication table.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class MySqlSchemaIntegrationTest {

    // Container as a bean: Spring stops it after the context closes, so shutdown hooks can still reach MySQL
    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlContainerConfig {

        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer("mysql:8.4");
        }
    }

    @Autowired
    private CustomerFacade customerFacade;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationsMatchEntitiesAndEventPublicationsComplete() {
        customerFacade.registerCustomer(new RegisterCustomerCommand("Jane Doe", "jane@example.com"));

        // CustomerRegisteredEvent is stored in event_publication and marked completed by the order module listener
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var publications = jdbcTemplate.queryForList(
                    "SELECT status, completion_date FROM event_publication WHERE event_type LIKE '%CustomerRegisteredEvent'");
            assertThat(publications).hasSize(1);
            assertThat(publications.getFirst().get("status")).isEqualTo("COMPLETED");
            assertThat(publications.getFirst().get("completion_date")).isNotNull();
        });
    }
}
