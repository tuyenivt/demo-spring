package com.example.versioning.controller;

import com.example.versioning.dto.EmployeeResponseV1;
import com.example.versioning.service.EmployeeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * V1 Employee controller (URI path versioning).
 * Deprecation, Sunset and Link headers are added by the API version deprecation handler in {@code ApiVersionConfig}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/employees")
@ConditionalOnBooleanProperty(name = "api.v1.enabled", matchIfMissing = true)
public class EmployeeControllerV1 {

    private final EmployeeService employeeService;

    @GetMapping
    @Deprecated
    @Operation(summary = "Get all employees (v1)", deprecated = true)
    @ApiResponse(responseCode = "200", description = "Returns v1 employee schema")
    public ResponseEntity<List<EmployeeResponseV1>> getEmployees() {
        return ResponseEntity.ok(employeeService.getAllEmployeesV1());
    }

    @GetMapping("/{id}")
    @Deprecated
    @Operation(summary = "Get one employee (v1)", deprecated = true)
    @ApiResponse(responseCode = "200", description = "Returns v1 employee schema")
    public ResponseEntity<EmployeeResponseV1> getEmployee(@PathVariable Long id) {
        return employeeService.getEmployeeV1(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
