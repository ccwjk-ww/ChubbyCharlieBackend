package com.example.server.controller;

import com.example.server.dto.LoginRequest;
import com.example.server.dto.LoginResponse;
import com.example.server.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * ⭐ Login Endpoint
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            LoginResponse errorResponse = new LoginResponse(
                    false,
                    "เกิดข้อผิดพลาดในการเข้าสู่ระบบ: " + e.getMessage(),
                    null,
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * ⭐ Logout Endpoint
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            authService.logout(token);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "ออกจากระบบสำเร็จ"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาดในการออกจากระบบ"
            ));
        }
    }

    /**
     * ⭐ Validate Token Endpoint
     * GET /api/auth/validate
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "valid", false,
                        "message", "Token not provided"
                ));
            }

            String token = authHeader.substring(7);
            boolean isValid = authService.validateToken(token);

            if (isValid) {
                return ResponseEntity.ok(Map.of(
                        "valid", true,
                        "message", "Token is valid"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "valid", false,
                        "message", "Token is invalid or expired"
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "valid", false,
                    "message", "Error validating token"
            ));
        }
    }

    /**
     * ⭐ Change Password Endpoint
     * POST /api/auth/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> request) {
        try {
            String token = authHeader.substring(7);
            Long empId = authService.getEmployeeIdFromToken(token);

            if (empId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "success", false,
                        "message", "Invalid token"
                ));
            }

            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");

            if (oldPassword == null || newPassword == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "กรุณากรอกรหัสผ่านเก่าและรหัสผ่านใหม่"
                ));
            }

            boolean success = authService.changePassword(empId, oldPassword, newPassword);

            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "เปลี่ยนรหัสผ่านสำเร็จ"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "message", "รหัสผ่านเก่าไม่ถูกต้อง"
                ));
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาดในการเปลี่ยนรหัสผ่าน"
            ));
        }
    }
}