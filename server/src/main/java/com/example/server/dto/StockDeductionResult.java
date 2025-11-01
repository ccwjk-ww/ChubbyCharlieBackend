package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Data
public class StockDeductionResult {
    private boolean success;
    private String message;
    private Long productId;
    private String productName;
    private Integer requestedQuantity;
    private Integer deductedQuantity;
    private Integer remainingStock;
    private List<String> errors;
}