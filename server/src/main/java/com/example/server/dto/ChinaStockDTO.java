package com.example.server.dto;

import lombok.Data;

import java.math.RoundingMode;
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
//    private BigDecimal avgShippingPerPair;
    private BigDecimal finalPricePerPair;
    private BigDecimal exchangeRate;
    // ⭐ เพิ่ม buffer fields
    private BigDecimal bufferPercentage;
    private Boolean includeBuffer;

    // Getter สำหรับ avgShippingPerPair (computed)
    public BigDecimal getAvgShippingPerPair() {
        if (shippingChinaToThaiBath != null && quantity != null && quantity > 0) {
            return shippingChinaToThaiBath.divide(
                    BigDecimal.valueOf(quantity), 3, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
    // StockLot info (without circular reference)
    private Long stockLotId;
    private String lotName;
}