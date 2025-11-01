package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.Employee;
import com.example.server.entity.EmployeeSalaryPayment;
import com.example.server.respository.EmployeeRepository;
import com.example.server.service.SalaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/salaries")
@CrossOrigin(origins = "*")
public class SalaryController {

    @Autowired
    private SalaryService salaryService;

    @Autowired
    private EmployeeRepository employeeRepository;

    // ============================================
    // GET Endpoints
    // ============================================

    @GetMapping
    public ResponseEntity<List<SalaryPaymentDTO>> getAllPayments() {
        List<EmployeeSalaryPayment> payments = salaryService.getAllPayments();
        List<SalaryPaymentDTO> dtos = payments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalaryPaymentDTO> getPaymentById(@PathVariable Long id) {
        Optional<EmployeeSalaryPayment> payment = salaryService.getPaymentById(id);
        return payment.map(p -> ResponseEntity.ok(toDTO(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<SalaryPaymentDTO>> getPaymentsByEmployee(@PathVariable Long employeeId) {
        List<EmployeeSalaryPayment> payments = salaryService.getPaymentsByEmployee(employeeId);
        List<SalaryPaymentDTO> dtos = payments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/month")
    public ResponseEntity<List<SalaryPaymentDTO>> getPaymentsByMonth(
            @RequestParam int year,
            @RequestParam int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<EmployeeSalaryPayment> payments = salaryService.getPaymentsByMonth(yearMonth);
        List<SalaryPaymentDTO> dtos = payments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<SalaryPaymentDTO>> getPendingPayments(
            @RequestParam int year,
            @RequestParam int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        List<EmployeeSalaryPayment> payments = salaryService.getPendingPayments(yearMonth);
        List<SalaryPaymentDTO> dtos = payments.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSalarySummary(
            @RequestParam int year,
            @RequestParam int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        Map<String, Object> summary = salaryService.getSalarySummary(yearMonth);
        return ResponseEntity.ok(summary);
    }

    // ============================================
    // POST Endpoints - Create & Process
    // ============================================

    /**
     * สร้าง Salary Payment แบบ Manual สำหรับพนักงานคนเดียว
     */
    @PostMapping
    public ResponseEntity<?> createSalaryPayment(@RequestBody SalaryPaymentCreateRequest request) {
        try {
            EmployeeSalaryPayment payment = toEntity(request);
            EmployeeSalaryPayment savedPayment = salaryService.createSalaryPayment(payment);
            return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(savedPayment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * ✅ จ่ายเงินเดือนหมู่สำหรับเดือนทั้งหมด
     */
    @PostMapping("/process-monthly")
    public ResponseEntity<?> processMonthlyPayments(@RequestBody MonthlySalaryProcessRequest request) {
        try {
            // แปลง List<DailyEmployeeWorkDays> เป็น Map<Long, Integer>
            Map<Long, Integer> dailyEmployeeWorkDays = new HashMap<>();
            if (request.getDailyEmployeeWorkDays() != null) {
                for (MonthlySalaryProcessRequest.DailyEmployeeWorkDays item : request.getDailyEmployeeWorkDays()) {
                    dailyEmployeeWorkDays.put(item.getEmployeeId(), item.getWorkDays());
                }
            }

            LocalDateTime paymentDate = request.getPaymentDate() != null ?
                    request.getPaymentDate() : LocalDateTime.now();

            Map<String, Object> result = salaryService.processMonthlyPayments(
                    request.getPaymentMonth(),
                    paymentDate,
                    dailyEmployeeWorkDays
            );

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to process payments: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ Trigger manual เงินเดือนรายเดือน (ไม่รอวันที่ 25)
     */
    @PostMapping("/trigger-auto-monthly")
    public ResponseEntity<?> triggerAutoMonthlyPayments() {
        try {
            salaryService.autoProcessMonthlySalaries();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Auto monthly salary processing triggered successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Failed to trigger auto processing: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // PATCH Endpoints - Update Status
    // ============================================

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            if (statusStr == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Status is required"
                ));
            }

            EmployeeSalaryPayment.PaymentStatus status =
                    EmployeeSalaryPayment.PaymentStatus.valueOf(statusStr.toUpperCase());

            EmployeeSalaryPayment updatedPayment = salaryService.updatePaymentStatus(id, status);
            return ResponseEntity.ok(toDTO(updatedPayment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Invalid status: " + e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ============================================
    // Helper Methods - DTO Conversion
    // ============================================

    private SalaryPaymentDTO toDTO(EmployeeSalaryPayment payment) {
        SalaryPaymentDTO dto = new SalaryPaymentDTO();
        dto.setPaymentId(payment.getPaymentId());
        dto.setEmployeeId(payment.getEmployee().getEmpId());
        dto.setEmployeeName(payment.getEmployee().getEmpName());
        dto.setEmployeeType(payment.getEmployee().getEmpType());
        dto.setPaymentMonth(payment.getPaymentMonth());
        dto.setAmount(payment.getAmount());
        dto.setPaymentDate(payment.getPaymentDate());
        dto.setStatus(payment.getStatus().name());
        dto.setType(payment.getType().name());
        dto.setWorkDays(payment.getWorkDays());
        dto.setNotes(payment.getNotes());
        dto.setTransactionId(payment.getTransactionId());
        dto.setCreatedDate(payment.getCreatedDate());
        return dto;
    }

    private EmployeeSalaryPayment toEntity(SalaryPaymentCreateRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
        payment.setEmployee(employee);
        payment.setPaymentMonth(request.getPaymentMonth());
        payment.setAmount(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate() != null ?
                request.getPaymentDate() : LocalDateTime.now());
        payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
        payment.setWorkDays(request.getWorkDays());
        payment.setNotes(request.getNotes());

        // ตั้งค่า type ตาม employee type
        if ("DAILY".equalsIgnoreCase(employee.getEmpType())) {
            payment.setType(EmployeeSalaryPayment.PaymentType.DAILY);
        } else {
            payment.setType(EmployeeSalaryPayment.PaymentType.MONTHLY);
        }

        return payment;
    }
}