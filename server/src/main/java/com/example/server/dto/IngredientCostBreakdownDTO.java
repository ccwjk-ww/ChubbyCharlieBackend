package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IngredientCostBreakdownDTO {
    private String ingredientName;
    private BigDecimal requiredQuantity;
    private String unit;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
    private BigDecimal costPercentage; // เปอร์เซ็นต์ของต้นทุนรวม
    private String stockSource; // ข้อมูล stock ที่มาจากไหน
}