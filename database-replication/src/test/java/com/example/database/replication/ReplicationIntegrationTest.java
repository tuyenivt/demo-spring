package com.example.database.replication;

import com.example.database.replication.dto.CreateUserRequest;
import com.example.database.replication.dto.UpdateUserRequest;
import com.example.database.replication.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ReplicationIntegrationTest {

    @Container
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Use same container for both writer and reader in tests
        registry.add("spring.datasource.writer.jdbc-url", mysql::getJdbcUrl);
        registry.add("spring.datasource.writer.username", mysql::getUsername);
        registry.add("spring.datasource.writer.password", mysql::getPassword);
        registry.add("spring.datasource.reader.jdbc-url", mysql::getJdbcUrl);
        registry.add("spring.datasource.reader.username", mysql::getUsername);
        registry.add("spring.datasource.reader.password", mysql::getPassword);
        registry.add("spring.liquibase.url", mysql::getJdbcUrl);
        registry.add("spring.liquibase.user", mysql::getUsername);
        registry.add("spring.liquibase.password", mysql::getPassword);
    }

    @Autowired
    private RestTestClient restTestClient;

    private UserResponse createUser(String name, String email) {
        return restTestClient.post().uri("/users")
                .body(new CreateUserRequest(name, email))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class)
                .returnResult()
                .getResponseBody();
    }

    @Test
    void shouldCreateAndReadUser() {
        // Create user
        var created = createUser("John Doe", "john@example.com");

        assertThat(created).isNotNull();
        assertThat(created.name()).isEqualTo("John Doe");
        assertThat(created.email()).isEqualTo("john@example.com");
        assertThat(created.id()).isNotNull();

        // Read user by ID
        restTestClient.get().uri("/users/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .isEqualTo(created);
    }

    @Test
    void shouldReturnNotFoundForNonExistentUser() {
        restTestClient.get().uri("/users/99999")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldValidateCreateUserRequest() {
        // Missing name and invalid email
        restTestClient.post().uri("/users")
                .body(new CreateUserRequest("", "invalid-email"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void shouldReturnConflictForDuplicateEmail() {
        createUser("First", "duplicate@example.com");

        restTestClient.post().uri("/users")
                .body(new CreateUserRequest("Second", "duplicate@example.com"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_ENTRY");
    }

    @Test
    void shouldDeleteUser() {
        // Create user first
        var userId = createUser("ToDelete", "delete@example.com").id();

        // Delete user
        restTestClient.delete().uri("/users/{id}", userId)
                .exchange()
                .expectStatus().isNoContent();

        // Verify user is gone
        restTestClient.get().uri("/users/{id}", userId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldFindUsersByName() {
        // Create users with same name
        createUser("SameName", "same1@example.com");
        createUser("SameName", "same2@example.com");

        // Find by name
        var users = restTestClient.get().uri("/users/name/SameName")
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<UserResponse>>() {
                })
                .returnResult()
                .getResponseBody();

        assertThat(users).hasSizeGreaterThanOrEqualTo(2)
                .extracting(UserResponse::name)
                .containsOnly("SameName");
    }

    @Test
    void shouldUpdateUser() {
        // Create user first
        var userId = createUser("Original Name", "original@example.com").id();
        var expected = new UserResponse(userId, "Updated Name", "updated@example.com");

        // Update user
        restTestClient.put().uri("/users/{id}", userId)
                .body(new UpdateUserRequest("Updated Name", "updated@example.com"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .isEqualTo(expected);

        // Verify update persisted
        restTestClient.get().uri("/users/{id}", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserResponse.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistentUser() {
        restTestClient.put().uri("/users/99999")
                .body(new UpdateUserRequest("Not Found", "notfound@example.com"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistentUser() {
        restTestClient.delete().uri("/users/99999")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void shouldValidateUpdateUserRequest() {
        // Create user first
        var userId = createUser("Test User", "test@example.com").id();

        // Try to update with invalid data
        restTestClient.put().uri("/users/{id}", userId)
                .body(new UpdateUserRequest("", "invalid-email"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }
}
