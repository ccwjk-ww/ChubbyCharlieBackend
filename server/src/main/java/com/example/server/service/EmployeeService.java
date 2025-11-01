// com.example.server.service.EmployeeService.java
package com.example.server.service;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.server.entity.Employee;
import com.example.server.respository.EmployeeRepository;

@Service
public class EmployeeService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Optional: For hashing passwords

    public List<Employee> getAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        employees.forEach(emp -> System.out.println("Employee ID: " + emp.getEmpId() + ", Status: " + emp.getStatus())); // Debug
        return employees;
    }

    public List<Employee> getEmployeesByStatus(Employee.Status status) {
        return employeeRepository.findByStatus(status);
    }

    public Optional<Employee> getEmployeeById(Long id) {
        return employeeRepository.findById(id);
    }

    public Employee createEmployee(Employee employee) {
        employee.setPassword(passwordEncoder.encode(employee.getPassword()));

        if ("DAILY".equalsIgnoreCase(employee.getEmpType())) {
            employee.setMonthlySalary(null);
        } else if ("MONTHLY".equalsIgnoreCase(employee.getEmpType())) {
            employee.setDailyWage(null);
        } else {
            throw new IllegalArgumentException("Invalid empType: Must be 'DAILY' or 'MONTHLY'");
        }

        if (employee.getStatus() == null) {
            employee.setStatus(Employee.Status.ACTIVE);
        }

        return employeeRepository.save(employee);
    }

    public Employee updateEmployee(Long id, Employee employeeDetails) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        employee.setEmpName(employeeDetails.getEmpName());
        employee.setEmpAddress(employeeDetails.getEmpAddress());
        employee.setEmpPhone(employeeDetails.getEmpPhone());
        employee.setEmpType(employeeDetails.getEmpType());
        employee.setDailyWage(employeeDetails.getDailyWage());
        employee.setMonthlySalary(employeeDetails.getMonthlySalary());
        employee.setRole(employeeDetails.getRole());
        employee.setUsername(employeeDetails.getUsername());
        if (employeeDetails.getPassword() != null && !employeeDetails.getPassword().isEmpty()) {
            employee.setPassword(passwordEncoder.encode(employeeDetails.getPassword()));
        }
        employee.setEmail(employeeDetails.getEmail());
        if (employeeDetails.getStatus() != null) {
            employee.setStatus(employeeDetails.getStatus());
        }

        if ("DAILY".equalsIgnoreCase(employee.getEmpType())) {
            employee.setMonthlySalary(null);
        } else if ("MONTHLY".equalsIgnoreCase(employee.getEmpType())) {
            employee.setDailyWage(null);
        } else {
            throw new IllegalArgumentException("Invalid empType: Must be 'DAILY' or 'MONTHLY'");
        }

        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    public List<Employee> searchEmployeesByName(String name) {
        return employeeRepository.findByEmpNameContainingIgnoreCase(name);
    }
}