package com.example.database.migration;

import com.example.database.migration.demo.repository.MigrationStateRepository;
import com.example.database.migration.demo.repository.ProductRepository;
import com.example.database.migration.demo.task.ProductTasks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "scheduled.enabled=false"  // Disable scheduled tasks in tests
})
@AutoConfigureMockMvc
@Testcontainers
class MainApplicationTests {

    @Container
    static MySQLContainer sourceMysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("demo")
            .withUsername("root")
            .withPassword("root");

    @Container
    static MySQLContainer targetMysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("demo")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("old-demo.datasource.jdbcUrl", sourceMysql::getJdbcUrl);
        registry.add("old-demo.datasource.username", sourceMysql::getUsername);
        registry.add("old-demo.datasource.password", sourceMysql::getPassword);

        registry.add("demo.datasource.jdbcUrl", targetMysql::getJdbcUrl);
        registry.add("demo.datasource.username", targetMysql::getUsername);
        registry.add("demo.datasource.password", targetMysql::getPassword);
    }

    @Autowired
    private ProductTasks productTasks;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MigrationStateRepository migrationStateRepository;

    @Autowired
    @Qualifier("oldDemoDataSource")
    private DataSource oldDemoDataSource;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // Verify that Spring context loads successfully with dual datasources and Flyway migrations
        // Flyway will automatically create all necessary tables in both databases
    }

    @Test
    void migrateCopiesOldProductsAndAdvancesState() throws Exception {
        // Given
        var updatedAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        var oldDemo = new JdbcTemplate(oldDemoDataSource);
        oldDemo.update("""
                INSERT INTO old_product (product_id, product_name, price, quality, date_of_manufacture, updated_at)
                VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                1L, "Legacy A", new BigDecimal("19.99"), 5L, updatedAt, updatedAt,
                2L, "Legacy B", new BigDecimal("5.50"), null, updatedAt, updatedAt.plusMinutes(1));

        // When
        productTasks.migrate();

        // Then
        var productA = productRepository.findById(1L).orElseThrow();
        assertThat(productA.getProductName()).isEqualTo("Legacy A");
        assertThat(productA.getPrice()).isEqualByComparingTo("19.99");
        assertThat(productA.getInStock()).isEqualTo(5L);
        assertThat(productA.getVendor()).isEqualTo("ABC");
        assertThat(productA.getUpdatedAt()).isEqualTo(updatedAt.minusHours(7));
        assertThat(productRepository.findById(2L).orElseThrow().getInStock()).isZero();

        var state = migrationStateRepository.findById("Product").orElseThrow();
        assertThat(state.getLastUpdatedAt()).isEqualTo(updatedAt.plusMinutes(1));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.migration.details.recordsProcessedInLastRun").value(2))
                .andExpect(jsonPath("$.components.migration.details.migrationRunning").value(false));
    }
}
