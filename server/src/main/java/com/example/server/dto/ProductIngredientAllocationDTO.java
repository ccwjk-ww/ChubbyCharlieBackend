// ProductIngredientAllocationDTO.java
package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductIngredientAllocationDTO {
    private Long allocationId;
    private Long stockItemId;
    private String stockItemName;
    private String stockType;
    private BigDecimal allocatedQuantity;
    private Integer allocationPriority;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
    private Integer availableQuantity;
    private String lotName;
    private Long stockLotId;
}