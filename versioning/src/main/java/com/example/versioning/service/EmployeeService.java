package com.example.versioning.service;

import com.example.versioning.dto.EmployeeResponse;
import com.example.versioning.dto.EmployeeResponseV1;
import com.example.versioning.dto.EmployeeResponseV2;
import com.example.versioning.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public List<EmployeeResponseV1> getAllEmployeesV1() {
        return employeeRepository.findAll().stream()
                .map(e -> new EmployeeResponseV1(e.getId(), e.getName(), e.getDepartment()))
                .toList();
    }

    public List<EmployeeResponseV2> getAllEmployeesV2() {
        return employeeRepository.findAll().stream()
                .map(e -> new EmployeeResponseV2(
                        e.getId(),
                        e.getName(),
                        e.getTitle(),
                        e.getDepartment(),
                        e.getHireDate(),
                        e.getStatus()
                ))
                .toList();
    }

    public Optional<EmployeeResponseV1> getEmployeeV1(Long id) {
        return employeeRepository.findById(id)
                .map(e -> new EmployeeResponseV1(e.getId(), e.getName(), e.getDepartment()));
    }

    public Optional<EmployeeResponseV2> getEmployeeV2(Long id) {
        return employeeRepository.findById(id)
                .map(e -> new EmployeeResponseV2(
                        e.getId(),
                        e.getName(),
                        e.getTitle(),
                        e.getDepartment(),
                        e.getHireDate(),
                        e.getStatus()
                ));
    }

    public List<EmployeeResponse> getEmployeesForView() {
        return employeeRepository.findAll().stream()
                .map(e -> new EmployeeResponse(
                        e.getId(),
                        e.getName(),
                        e.getDepartment(),
                        e.getTitle(),
                        e.getHireDate(),
                        e.getStatus()
                ))
                .toList();
    }
}
