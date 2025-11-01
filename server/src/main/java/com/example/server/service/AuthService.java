package com.example.server.service;

import com.example.server.dto.EmployeeDTO;
import com.example.server.dto.LoginRequest;
import com.example.server.dto.LoginResponse;
import com.example.server.entity.Employee;
import com.example.server.respository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * ⭐ Login - ตรวจสอบ username และ password
     */
    public LoginResponse login(LoginRequest request) {
        // 1. ตรวจสอบว่ามี username และ password หรือไม่
        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            return new LoginResponse(false, "กรุณากระอกชื่อผู้ใช้", null, null);
        }

        if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
            return new LoginResponse(false, "กรุณากรอกรหัสผ่าน", null, null);
        }

        // 2. ค้นหา Employee ด้วย username
        Optional<Employee> employeeOpt = employeeRepository.findByUsername(request.getUsername());

        if (!employeeOpt.isPresent()) {
            return new LoginResponse(false, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง", null, null);
        }

        Employee employee = employeeOpt.get();

        // 3. ตรวจสอบสถานะพนักงาน
        if (employee.getStatus() == Employee.Status.INACTIVE) {
            return new LoginResponse(false, "บัญชีของคุณถูกปิดการใช้งาน กรุณาติดต่อผู้ดูแลระบบ", null, null);
        }

        // 4. ตรวจสอบรหัสผ่าน
        if (!passwordEncoder.matches(request.getPassword(), employee.getPassword())) {
            return new LoginResponse(false, "ชื่อผู้ใช้หรือรหัสผ่านไม่ถูกต้อง", null, null);
        }

        // 5. ตรวจสอบสิทธิ์ (ADMIN หรือ MANAGER เท่านั้น)
        if (!isAuthorizedRole(employee.getRole())) {
            return new LoginResponse(false, "คุณไม่มีสิทธิ์เข้าใช้งานระบบนี้", null, null);
        }

        // 6. Login สำเร็จ - สร้าง EmployeeDTO (ไม่รวม password)
        EmployeeDTO employeeDTO = convertToDTO(employee);

        // 7. สร้าง token (ถ้าต้องการใช้ JWT)
        String token = generateToken(employee);

        return new LoginResponse(true, "เข้าสู่ระบบสำเร็จ", token, employeeDTO);
    }

    /**
     * ⭐ ตรวจสอบว่า Role มีสิทธิ์เข้าถึงระบบหรือไม่
     * ADMIN และ MANAGER สามารถทำได้ทุกอย่าง
     */
    private boolean isAuthorizedRole(String role) {
        if (role == null) return false;
        String normalizedRole = role.toUpperCase().trim();
        return normalizedRole.equals("ADMIN") || normalizedRole.equals("MANAGER");
    }

    /**
     * ⭐ แปลง Employee entity เป็น EmployeeDTO (ไม่รวม password)
     */
    private EmployeeDTO convertToDTO(Employee employee) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setEmpId(employee.getEmpId());
        dto.setEmpName(employee.getEmpName());
        dto.setEmpAddress(employee.getEmpAddress());
        dto.setEmpPhone(employee.getEmpPhone());
        dto.setEmpType(employee.getEmpType());
        dto.setDailyWage(employee.getDailyWage());
        dto.setMonthlySalary(employee.getMonthlySalary());
        dto.setRole(employee.getRole());
        dto.setUsername(employee.getUsername());
        dto.setEmail(employee.getEmail());
        dto.setStatus(employee.getStatus() != null ? employee.getStatus().name() : null);
        return dto;
    }

    /**
     * ⭐ สร้าง Token (Simple version - ใช้ UUID)
     * สำหรับ production ควรใช้ JWT
     */
    private String generateToken(Employee employee) {
        // Simple token = "EMP-{empId}-{timestamp}"
        // ใน production ควรใช้ JWT library
        return "EMP-" + employee.getEmpId() + "-" + System.currentTimeMillis();
    }

    /**
     * ⭐ Validate Token (Simple version)
     * ใน production ควรใช้ JWT validation
     */
    public boolean validateToken(String token) {
        if (token == null || !token.startsWith("EMP-")) {
            return false;
        }
        // Simple validation - ตรวจสอบว่า token ยังไม่หมดอายุ (24 ชั่วโมง)
        try {
            String[] parts = token.split("-");
            if (parts.length != 3) return false;

            long timestamp = Long.parseLong(parts[2]);
            long currentTime = System.currentTimeMillis();
            long expirationTime = 24 * 60 * 60 * 1000; // 24 hours

            return (currentTime - timestamp) < expirationTime;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ⭐ Get Employee ID from Token
     */
    public Long getEmployeeIdFromToken(String token) {
        try {
            String[] parts = token.split("-");
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * ⭐ Logout (invalidate token)
     * ใน production อาจต้องเก็บ blacklist ของ token ที่ logout แล้ว
     */
    public boolean logout(String token) {
        // สำหรับ simple version ไม่ต้องทำอะไร
        // Client จะลบ token เอง
        // ใน production อาจเพิ่ม token blacklist
        return true;
    }

    /**
     * ⭐ Change Password
     */
    public boolean changePassword(Long empId, String oldPassword, String newPassword) {
        Optional<Employee> employeeOpt = employeeRepository.findById(empId);

        if (!employeeOpt.isPresent()) {
            return false;
        }

        Employee employee = employeeOpt.get();

        // ตรวจสอบรหัสผ่านเก่า
        if (!passwordEncoder.matches(oldPassword, employee.getPassword())) {
            return false;
        }

        // เปลี่ยนรหัสผ่านใหม่
        employee.setPassword(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);

        return true;
    }
}