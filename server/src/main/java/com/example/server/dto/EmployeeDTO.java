package com.example.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeDTO {
    private Long empId;
    private String empName;
    private String empAddress;
    private String empPhone;
    private String empType;
    private Double dailyWage;
    private Double monthlySalary;
    private String role;           // ADMIN, MANAGER, EMPLOYEE
    private String username;
    private String email;
    private String status;
    // ⚠️ ไม่รวม password เพื่อความปลอดภัย
}