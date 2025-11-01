package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductCostAnalysisDTO {
    private Long productId;
    private String productName;
    private BigDecimal totalMaterialCost;
    private BigDecimal sellingPrice;
    private BigDecimal grossProfit;
    private BigDecimal profitMarginPercentage;
    private List<IngredientCostBreakdownDTO> ingredientBreakdown;
}