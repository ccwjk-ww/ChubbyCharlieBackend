package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Data
public class SalaryPaymentDTO {
    private Long paymentId;
    private Long employeeId;
    private String employeeName;
    private String employeeType;
    private YearMonth paymentMonth;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private String status;
    private String type;
    private Integer workDays;
    private String notes;
    private Long transactionId;
    private LocalDateTime createdDate;
}

@Data
class SalarySummaryDTO {
    private YearMonth month;
    private BigDecimal totalMonthlyPaid;
    private BigDecimal totalDailyPaid;
    private BigDecimal grandTotal;
    private Integer monthlyEmployeeCount;
    private Integer dailyEmployeeCount;
    private List<EmployeeSalaryDetail> details;

    @Data
    public static class EmployeeSalaryDetail {
        private Long employeeId;
        private String employeeName;
        private String type;
        private BigDecimal amount;
        private Integer workDays;
        private String status;
    }
}