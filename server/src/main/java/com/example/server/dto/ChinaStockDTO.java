package com.example.server.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
public class ChinaStockDTO {
    private Long stockItemId;
    private String name;
    private LocalDateTime lotDate;
    private String shopURL;
    private String status;

    // China-specific fields
    private BigDecimal unitPriceYuan;
    private Integer quantity;
    private BigDecimal totalValueYuan;
    private BigDecimal shippingWithinChinaYuan;
    private BigDecimal totalYuan;
    private BigDecimal totalBath;
    private BigDecimal pricePerUnitBath;
    private BigDecimal shippingChinaToThaiBath;
    private BigDecimal avgShippingPerPair;
    private BigDecimal finalPricePerPair;
    private BigDecimal exchangeRate;

    // StockLot info (without circular reference)
    private Long stockLotId;
    private String lotName;
}