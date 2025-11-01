package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionCreateRequest {
    private String type; // "INCOME" or "EXPENSE"
    private String category;
    private BigDecimal amount;
    private String description;
    private Long orderId;
    private Long stockLotId;
    private Long employeeId;
    private LocalDateTime transactionDate;
    private String notes;
}
