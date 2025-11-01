package com.example.server.respository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.server.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    List<Employee> findByEmpNameContainingIgnoreCase(String empName);
    List<Employee> findByStatus(Employee.Status status);

    // ⭐ เพิ่ม method สำหรับค้นหาด้วย username (สำหรับ login)
    Optional<Employee> findByUsername(String username);

    // ⭐ ตรวจสอบว่า username ซ้ำหรือไม่
    boolean existsByUsername(String username);
}