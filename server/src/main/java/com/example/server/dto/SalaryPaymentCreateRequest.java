package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Data
public class SalaryPaymentCreateRequest {
    private Long employeeId;
    private YearMonth paymentMonth;
    private BigDecimal amount;
    private LocalDateTime paymentDate;
    private Integer workDays; // สำหรับพนักงานรายวันเท่านั้น
    private String notes;
}
