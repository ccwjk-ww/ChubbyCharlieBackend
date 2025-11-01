package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type; // INCOME, EXPENSE

    @Column(name = "transaction_number", nullable = true)  // เปลี่ยนเป็น nullable
    private String transactionNumber;

//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private TransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50) // ⬅️ เพิ่ม length = 50
    private TransactionCategory category;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    // Reference IDs (nullable - ขึ้นอยู่กับ category)
    private Long orderId;
    private Long stockLotId;
    private Long employeeId;
    private Long salaryPaymentId;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionMode mode; // AUTO, MANUAL

    private String createdBy; // username ของผู้สร้าง (สำหรับ manual)

    @Column(length = 1000)
    private String notes;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @PrePersist
    public void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
        if (transactionDate == null) {
            transactionDate = LocalDateTime.now();
        }
    }
    public void generateTransactionNumber() {
        if (this.transactionNumber == null) {
            this.transactionNumber = "TXN-" + System.currentTimeMillis();
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    // Enums
    public enum TransactionType {
        INCOME,   // รายรับ
        EXPENSE   // รายจ่าย
    }
    public enum TransactionCategory {
        ORDER_PAYMENT,
        SERVICE_INCOME,
        OTHER_INCOME,
        STOCK_PURCHASE,
        SALARY_DAILY,
        SALARY_MONTHLY,
        SHIPPING_COST,
        OFFICE_SUPPLIES,
        MARKETING,
        MAINTENANCE,
        RENT,
        OTHER_EXPENSE
    }
//    public enum TransactionCategory {
//        // INCOME categories
//        ORDER_PAYMENT,        // การชำระเงินจากคำสั่งซื้อ
//        OTHER_INCOME,         // รายรับอื่นๆ
//
//        // EXPENSE categories
//        STOCK_PURCHASE,       // ซื้อ Stock/Lot ใหม่
//        SALARY_DAILY,         // เงินเดือนพนักงานรายวัน
//        SALARY_MONTHLY,       // เงินเดือนพนักงานรายเดือน
//        RENT,                 // ค่าเช่า
//        UTILITIES_WATER,      // ค่าน้ำ
//        UTILITIES_ELECTRIC,   // ค่าไฟ
//        TRANSPORTATION,       // ค่าขนส่ง
//        OFFICE_SUPPLIES,      // ค่าอุปกรณ์สำนักงาน
//        MAINTENANCE,          // ค่าซ่อมบำรุง
//        MARKETING,            // ค่าการตลาด
//        OTHER_EXPENSE         // รายจ่ายอื่นๆ
//    }

    public enum TransactionMode {
        AUTO,     // สร้างอัตโนมัติจากระบบ
        MANUAL    // สร้างด้วยตนเอง
    }
}