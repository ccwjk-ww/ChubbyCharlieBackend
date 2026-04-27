package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ThaiStockDTO {
    private Long stockItemId;
    private String name;
    private LocalDateTime lotDate;
    private String shopURL;
    private String status;

    // Quantity Management
    private Integer originalQuantity;
    private Integer currentQuantity;
    private Integer usedQuantity;
    private BigDecimal usagePercentage;
    private BigDecimal remainingPercentage;
    private Integer quantity; // backward compatibility

    // ⭐ NEW: Defective Quantity fields
    private Integer defectiveQuantity;
    private BigDecimal defectiveValue; // มูลค่าของเสีย = defectiveQty × unitCost

    // Thai-specific fields
    private BigDecimal priceTotal;
    private BigDecimal shippingCost;
    private BigDecimal pricePerUnit;
    private BigDecimal pricePerUnitWithShipping;

    private BigDecimal vatPercentage;
    private Boolean includeVat;

    // StockLot info
    private Long stockLotId;
    private String lotName;
    private BigDecimal totalCost;
}