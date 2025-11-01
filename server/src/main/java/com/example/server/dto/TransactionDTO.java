package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionDTO {
    private Long transactionId;
    private String type;
    private String category;
    private BigDecimal amount;
    private String description;
    private Long orderId;
    private Long stockLotId;
    private Long employeeId;
    private Long salaryPaymentId;
    private LocalDateTime transactionDate;
    private String mode;
    private String createdBy;
    private String notes;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}

@Data
class TransactionSummaryDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private Long totalTransactions;
    private Long incomeCount;
    private Long expenseCount;

    // Breakdown by category
    private BigDecimal orderPayments;
    private BigDecimal stockPurchases;
    private BigDecimal salaryPayments;
    private BigDecimal utilities;
    private BigDecimal otherExpenses;
}

@Data
class MonthlyReportDTO {
    private Integer year;
    private Integer month;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netProfit;
    private BigDecimal profitMargin; // เปอร์เซ็นต์กำไร

    // Category breakdown
    private CategoryBreakdown incomeBreakdown;
    private CategoryBreakdown expenseBreakdown;

    @Data
    public static class CategoryBreakdown {
        private BigDecimal orderPayments;
        private BigDecimal stockPurchases;
        private BigDecimal salaries;
        private BigDecimal rent;
        private BigDecimal utilities;
        private BigDecimal transportation;
        private BigDecimal marketing;
        private BigDecimal others;
    }
}

@Data
class DateRangeTransactionRequest {
    private LocalDateTime startDate;
    private LocalDateTime endDate;
}