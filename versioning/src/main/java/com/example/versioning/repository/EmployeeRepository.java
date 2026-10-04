package com.example.versioning.repository;

import com.example.versioning.entity.Employee;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

public interface EmployeeRepository extends ListPagingAndSortingRepository<Employee, Long>, ListCrudRepository<Employee, Long> {
}
