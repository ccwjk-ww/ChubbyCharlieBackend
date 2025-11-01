package com.example.server.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class ThaiStockDTO {
    private Long stockItemId;
    private String name;
    private LocalDateTime lotDate;
    private String shopURL;
    private String status;

    // Thai-specific fields
    private Integer quantity;
    private BigDecimal priceTotal;
    private BigDecimal shippingCost;
    private BigDecimal pricePerUnit;
    private BigDecimal pricePerUnitWithShipping;

    // StockLot info (without circular reference)
    private Long stockLotId;
    private String lotName;
}