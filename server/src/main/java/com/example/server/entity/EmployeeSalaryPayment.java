package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Entity
@Data
@Table(name = "employee_salary_payments")
public class EmployeeSalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private YearMonth paymentMonth; // เดือนที่จ่าย (เช่น 2025-01)

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount; // จำนวนเงินที่จ่าย

    @Column(nullable = false)
    private LocalDateTime paymentDate; // วันที่จ่ายจริง

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type; // DAILY or MONTHLY

    // สำหรับ DAILY เท่านั้น
    private Integer workDays; // จำนวนวันทำงาน

    @Column(length = 1000)
    private String notes;

    private Long transactionId; // เชื่อมกับ Transaction

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @PrePersist
    public void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
        if (paymentDate == null) {
            paymentDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    public enum PaymentStatus {
        PENDING,    // รอจ่าย
        PAID,       // จ่ายแล้ว
        CANCELLED   // ยกเลิก
    }

    public enum PaymentType {
        DAILY,      // พนักงานรายวัน
        MONTHLY     // พนักงานรายเดือน
    }
}