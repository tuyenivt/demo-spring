package com.example.versioning.controller;

import com.example.versioning.dto.EmployeeResponse;
import com.example.versioning.dto.Views;
import com.example.versioning.service.EmployeeService;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employees/view")
public class EmployeeViewController {

    private final EmployeeService employeeService;

    @GetMapping(version = "1")
    @JsonView(Views.V1.class)
    public List<EmployeeResponse> getV1View() {
        return employeeService.getEmployeesForView();
    }

    @GetMapping(version = "2")
    @JsonView(Views.V2.class)
    public List<EmployeeResponse> getV2View() {
        return employeeService.getEmployeesForView();
    }
}
