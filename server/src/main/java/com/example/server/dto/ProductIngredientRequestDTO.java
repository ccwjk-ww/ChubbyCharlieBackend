package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductIngredientRequestDTO {
    private Long stockItemId; // ถ้าเลือกจาก stock โดยตรง
    private String ingredientName;
    private BigDecimal requiredQuantity;
    private String unit;
    private String notes;
}