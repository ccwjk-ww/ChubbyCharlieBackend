// ProductIngredientRequestDTO.java - อัปเดต
package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductIngredientRequestDTO {
    private String ingredientName;
    private BigDecimal requiredQuantity;
    private String unit;
    private String notes;

    // ⭐ สำหรับโหมด SINGLE (แบบเดิม)
    private Long stockItemId;

    // ⭐ สำหรับโหมด MULTI_LOT (แบบใหม่)
    private String allocationMode; // "SINGLE" หรือ "MULTI_LOT"
    private List<StockAllocationRequest> stockAllocations;

    @Data
    public static class StockAllocationRequest {
        private Long stockItemId;
        private BigDecimal allocatedQuantity;
        private Integer allocationPriority;
    }
}