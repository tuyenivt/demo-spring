package com.example.graphql.controller;

import com.example.graphql.config.GraphQLConfig;
import com.example.graphql.dto.input.CreateStudentInput;
import com.example.graphql.dto.pagination.*;
import com.example.graphql.entity.Student;
import com.example.graphql.exception.GraphQLErrorHandler;
import com.example.graphql.exception.ResourceNotFoundException;
import com.example.graphql.exception.ValidationException;
import com.example.graphql.repository.VehicleRepository;
import com.example.graphql.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.graphql.test.autoconfigure.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Import(GraphQLConfig.class)
@GraphQlTest(controllers = {StudentController.class, GraphQLErrorHandler.class})
class StudentControllerTest {

    @Autowired
    private GraphQlTester graphQlTester;

    @MockitoBean
    private StudentService studentService;

    @MockitoBean
    private VehicleRepository vehicleRepository;

    @Test
    void student_shouldReturnStudentById() {
        var studentId = UUID.randomUUID();
        var student = createTestStudent(studentId, "John Doe", "123 Main St", LocalDate.of(2000, 1, 1));

        when(studentService.findById(studentId.toString())).thenReturn(student);

        graphQlTester.document("""
                        query($id: UUID!) {
                            student(id: $id) {
                                id
                                name
                                address
                                dateOfBirth
                            }
                        }
                        """)
                .variable("id", studentId)
                .execute()
                .path("student.id").entity(UUID.class).isEqualTo(studentId)
                .path("student.name").entity(String.class).isEqualTo("John Doe")
                .path("student.address").entity(String.class).isEqualTo("123 Main St");
    }

    @Test
    void student_shouldReturnErrorWhenNotFound() {
        var studentId = UUID.randomUUID();
        when(studentService.findById(studentId.toString())).thenThrow(new ResourceNotFoundException("Student", studentId));

        graphQlTester.document("""
                        query($id: UUID!) {
                            student(id: $id) {
                                id
                                name
                            }
                        }
                        """)
                .variable("id", studentId)
                .execute()
                .errors()
                .expect(error -> error.getMessage().contains("not found"));
    }

    @Test
    void studentsPage_shouldReturnPagedResults() {
        var student = createTestStudent(UUID.randomUUID(), "Jane Doe", "456 Oak Ave", LocalDate.of(1998, 5, 15));
        var pageResult = new PageResult<>(List.of(student), new PageInfoDto(1, 1, 0, 10, false, false));

        when(studentService.findPage(any(), any(), any())).thenReturn(pageResult);

        graphQlTester.document("""
                        query {
                            studentsPage(page: { page: 0, size: 10 }) {
                                content {
                                    id
                                    name
                                }
                                pageInfo {
                                    totalElements
                                    hasNext
                                }
                            }
                        }
                        """)
                .execute()
                .path("studentsPage.content").entityList(Object.class).hasSize(1)
                .path("studentsPage.pageInfo.totalElements").entity(Integer.class).isEqualTo(1)
                .path("studentsPage.pageInfo.hasNext").entity(Boolean.class).isEqualTo(false);
    }

    @Test
    void studentsConnection_shouldReturnCursorBasedResults() {
        var student = createTestStudent(UUID.randomUUID(), "Bob Smith", "789 Elm St", LocalDate.of(1999, 8, 20));
        var connection = new Connection<>(
                List.of(new Edge<>(student, "cursor123")),
                new PageInfoConnection(true, false, "cursor123", "cursor123"),
                1
        );

        when(studentService.findConnection(any(), any(), any())).thenReturn(connection);

        graphQlTester.document("""
                        query {
                            studentsConnection(connection: { first: 10 }) {
                                edges {
                                    node {
                                        id
                                        name
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
                .path("studentsConnection.edges").entityList(Object.class).hasSize(1)
                .path("studentsConnection.totalCount").entity(Integer.class).isEqualTo(1)
                .path("studentsConnection.pageInfo.hasNextPage").entity(Boolean.class).isEqualTo(true);
    }

    @Test
    void createStudent_shouldCreateWithValidInput() {
        var studentId = UUID.randomUUID();
        var input = new CreateStudentInput("Alice Brown", "321 Pine Rd", "2000-03-10");
        var createdStudent = createTestStudent(studentId, input.name(), input.address(), LocalDate.of(2000, 3, 10));

        when(studentService.create(any(CreateStudentInput.class))).thenReturn(createdStudent);

        graphQlTester.document("""
                        mutation($input: CreateStudentInput!) {
                            createStudent(input: $input) {
                                id
                                name
                                address
                                dateOfBirth
                            }
                        }
                        """)
                .variable("input", Map.of(
                        "name", "Alice Brown",
                        "address", "321 Pine Rd",
                        "dateOfBirth", "2000-03-10"
                ))
                .execute()
                .path("createStudent.name").entity(String.class).isEqualTo("Alice Brown");
    }

    @Test
    void createStudent_shouldReturnValidationErrorForInvalidInput() {
        var fieldErrors = Map.of("name", "Name must be at least 2 characters");
        var exception = new ValidationException(
                "Student creation validation failed",
                fieldErrors
        );

        when(studentService.create(any(CreateStudentInput.class))).thenThrow(exception);

        graphQlTester.document("""
                        mutation($input: CreateStudentInput!) {
                            createStudent(input: $input) {
                                id
                                name
                            }
                        }
                        """)
                .variable("input", Map.of(
                        "name", "J",
                        "dateOfBirth", "2000-01-01"
                ))
                .execute()
                .errors()
                .expect(error -> {
                    assertThat(error.getMessage()).contains("validation failed");
                    assertThat(error.getExtensions()).containsKey("fieldErrors");
                    return true;
                });
    }

    @Test
    void createStudents_shouldBulkCreate() {
        var student1 = createTestStudent(UUID.randomUUID(), "Student 1", "Addr 1", LocalDate.of(2000, 1, 1));
        var student2 = createTestStudent(UUID.randomUUID(), "Student 2", "Addr 2", LocalDate.of(2001, 2, 2));

        when(studentService.createAll(any())).thenReturn(List.of(student1, student2));

        graphQlTester.document("""
                        mutation($inputs: [CreateStudentInput!]!) {
                            createStudents(inputs: $inputs) {
                                id
                                name
                            }
                        }
                        """)
                .variable("inputs", List.of(
                        Map.of("name", "Student 1", "address", "Addr 1", "dateOfBirth", "2000-01-01"),
                        Map.of("name", "Student 2", "address", "Addr 2", "dateOfBirth", "2001-02-02")
                ))
                .execute()
                .path("createStudents").entityList(Object.class).hasSize(2);
    }

    private Student createTestStudent(UUID id, String name, String address, LocalDate dateOfBirth) {
        var student = Student.builder()
                .name(name)
                .address(address)
                .dateOfBirth(dateOfBirth)
                .vehicles(Collections.emptyList())
                .build();
        student.setId(id);
        student.setCreatedAt(OffsetDateTime.now());
        student.setUpdatedAt(OffsetDateTime.now());
        return student;
    }
}
