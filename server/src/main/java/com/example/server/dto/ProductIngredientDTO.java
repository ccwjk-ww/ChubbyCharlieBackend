package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductIngredientDTO {
    private Long ingredientId;
    private Long productId;
    private Long stockItemId;
    private String ingredientName;
    private BigDecimal requiredQuantity;
    private String unit;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
    private String notes;

    // ข้อมูลจาก Stock Item
    private String stockItemName;
    private String stockType; // "CHINA" or "THAI"
    private BigDecimal availableQuantity;
}

