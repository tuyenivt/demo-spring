package com.example.graphql.controller;

import com.example.graphql.config.GraphQLConfig;
import com.example.graphql.dto.input.CreateVehicleInput;
import com.example.graphql.dto.pagination.*;
import com.example.graphql.entity.Student;
import com.example.graphql.entity.Vehicle;
import com.example.graphql.enums.VehicleType;
import com.example.graphql.exception.GraphQLErrorHandler;
import com.example.graphql.exception.ValidationException;
import com.example.graphql.repository.StudentRepository;
import com.example.graphql.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(GraphQLConfig.class)
@GraphQlTest(controllers = {VehicleController.class, GraphQLErrorHandler.class})
class VehicleControllerTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private VehicleService vehicleService;

    @MockitoBean
    private StudentRepository studentRepository;

    @Test
    void vehiclesPage_shouldReturnPagedResults() {
        var vehicle = createTestVehicle(UUID.randomUUID(), VehicleType.CAR, null);
        var pageResult = new PageResult<>(
                List.of(vehicle),
                new PageInfoDto(1, 1, 0, 10, false, false)
        );

        when(vehicleService.findPage(any(), any(), any())).thenReturn(pageResult);

        graphQlTester.document("""
                        query {
                            vehiclesPage(page: { page: 0, size: 10 }) {
                                content {
                                    id
                                    type
                                }
                                pageInfo {
                                    totalElements
                                    hasNext
                                }
                            }
                        }
                        """)
                .execute()
                .path("vehiclesPage.content").entityList(Object.class).hasSize(1)
                .path("vehiclesPage.pageInfo.totalElements").entity(Integer.class).isEqualTo(1);
    }

    @Test
    void vehiclesConnection_shouldReturnCursorBasedResults() {
        var vehicle = createTestVehicle(UUID.randomUUID(), VehicleType.MOTORCYCLE, null);
        var connection = new Connection<>(
                List.of(new Edge<>(vehicle, "cursor123")),
                new PageInfoConnection(true, false, "cursor123", "cursor123"),
                1
        );

        when(vehicleService.findConnection(any(), any(), any())).thenReturn(connection);

        graphQlTester.document("""
                        query {
                            vehiclesConnection(connection: { first: 10 }) {
                                edges {
                                    node {
                                        id
                                        type
                                    }
                                    cursor
                                }
                                pageInfo {
                                    hasNextPage
                                }
                                totalCount
                            }
                        }
                        """)
                .execute()
                .path("vehiclesConnection.edges").entityList(Object.class).hasSize(1)
                .path("vehiclesConnection.totalCount").entity(Integer.class).isEqualTo(1);
    }

    @Test
    void createVehicle_shouldCreateWithValidInput() {
        var vehicleId = UUID.randomUUID();
        var studentId = UUID.randomUUID();
        var student = createTestStudent(studentId, "John Doe", LocalDate.of(1990, 1, 1));
        var vehicle = createTestVehicle(vehicleId, VehicleType.CAR, student);

        when(vehicleService.create(any(CreateVehicleInput.class))).thenReturn(vehicle);

        graphQlTester.document("""
                        mutation($input: CreateVehicleInput!) {
                            createVehicle(input: $input) {
                                id
                                type
                            }
                        }
                        """)
                .variable("input", Map.of(
                        "type", "CAR",
                        "studentId", studentId.toString()
                ))
                .execute()
                .path("createVehicle.type").entity(String.class).isEqualTo("CAR");
    }

    @Test
    void createVehicle_shouldReturnValidationErrorForInvalidAge() {
        var exception = new ValidationException(
                com.example.graphql.exception.ErrorCode.VEHICLE_ASSIGNMENT_ERROR,
                "Student is too young to have a car"
        );

        when(vehicleService.create(any(CreateVehicleInput.class))).thenThrow(exception);

        graphQlTester.document("""
                        mutation($input: CreateVehicleInput!) {
                            createVehicle(input: $input) {
                                id
                                type
                            }
                        }
                        """)
                .variable("input", Map.of(
                        "type", "CAR",
                        "studentId", UUID.randomUUID().toString()
                ))
                .execute()
                .errors()
                .expect(error -> {
                    assertThat(error.getMessage()).contains("too young");
                    return true;
                });
    }

    @Test
    void createVehicles_shouldBulkCreate() {
        var vehicle1 = createTestVehicle(UUID.randomUUID(), VehicleType.BICYCLE, null);
        var vehicle2 = createTestVehicle(UUID.randomUUID(), VehicleType.SCOOTER, null);

        when(vehicleService.createAll(any())).thenReturn(List.of(vehicle1, vehicle2));

        graphQlTester.document("""
                        mutation($inputs: [CreateVehicleInput!]!) {
                            createVehicles(inputs: $inputs) {
                                id
                                type
                            }
                        }
                        """)
                .variable("inputs", List.of(
                        Map.of("type", "BICYCLE"),
                        Map.of("type", "SCOOTER")
                ))
                .execute()
                .path("createVehicles").entityList(Object.class).hasSize(2);
    }

    @Test
    void vehiclesPage_withTypeFilter_shouldFilterResults() {
        var vehicle = createTestVehicle(UUID.randomUUID(), VehicleType.CAR, null);
        var pageResult = new PageResult<>(
                List.of(vehicle),
                new PageInfoDto(1, 1, 0, 10, false, false)
        );

        when(vehicleService.findPage(any(), any(), any())).thenReturn(pageResult);

        graphQlTester.document("""
                        query {
                            vehiclesPage(
                                page: { page: 0, size: 10 }
                                filter: { type: { eq: CAR } }
                            ) {
                                content {
                                    id
                                    type
                                }
                                pageInfo {
                                    totalElements
                                }
                            }
                        }
                        """)
                .execute()
                .path("vehiclesPage.content").entityList(Object.class).hasSize(1);
    }

    private Vehicle createTestVehicle(UUID id, VehicleType type, Student student) {
        var vehicle = Vehicle.builder()
                .type(type)
                .student(student)
                .build();
        vehicle.setId(id);
        vehicle.setCreatedAt(OffsetDateTime.now());
        vehicle.setUpdatedAt(OffsetDateTime.now());
        return vehicle;
    }

    private Student createTestStudent(UUID id, String name, LocalDate dateOfBirth) {
        var student = Student.builder()
                .name(name)
                .dateOfBirth(dateOfBirth)
                .build();
        student.setId(id);
        return student;
    }
}
