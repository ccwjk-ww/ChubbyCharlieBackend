// com.example.server.entity.Employee.java
package com.example.server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long empId;

    private String empName;
    private String empAddress;
    private String empPhone;
    private String empType; // e.g., "DAILY" or "MONTHLY"

    private Double dailyWage; // Used if empType is "DAILY", can be null otherwise
    private Double monthlySalary; // Used if empType is "MONTHLY", can be null otherwise

    private String role;
    private String username;
    private String password; // Should be hashed in production
    private String email;

    // New status field with @Enumerated to map as string
    @Enumerated(EnumType.STRING)
    private Status status; // Default to ACTIVE if not specified

    // Enum for status
    public enum Status {
        ACTIVE,
        INACTIVE
    }
}