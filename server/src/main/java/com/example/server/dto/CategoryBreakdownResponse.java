package com.example.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryBreakdownResponse {
    private String category;         // "ORDER_PAYMENT", "STOCK_PURCHASE", etc.
    private BigDecimal amount;
    private Integer count;
    private Double percentage;      // เปอร์เซ็นต์ของยอดรวม

    public CategoryBreakdownResponse(String category, BigDecimal amount, Integer count) {
        this.category = category;
        this.amount = amount;
        this.count = count;
        this.percentage = 0.0;
    }
}