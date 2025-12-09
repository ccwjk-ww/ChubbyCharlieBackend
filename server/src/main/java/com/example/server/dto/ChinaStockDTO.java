//package com.example.server.dto;
//
//import lombok.Data;
//
//import java.math.RoundingMode;
//import java.time.LocalDateTime;
//import java.math.BigDecimal;
//
//@Data
//public class ChinaStockDTO {
//    private Long stockItemId;
//    private String name;
//    private LocalDateTime lotDate;
//    private String shopURL;
//    private String status;
//
//    // China-specific fields
//    private BigDecimal unitPriceYuan;
//    private Integer quantity;
//    private BigDecimal totalValueYuan;
//    private BigDecimal shippingWithinChinaYuan;
//    private BigDecimal totalYuan;
//    private BigDecimal totalBath;
//    private BigDecimal pricePerUnitBath;
//    private BigDecimal shippingChinaToThaiBath;
////    private BigDecimal avgShippingPerPair;
//    private BigDecimal finalPricePerPair;
//    private BigDecimal exchangeRate;
//    // ⭐ เพิ่ม buffer fields
//    private BigDecimal bufferPercentage;
//    private Boolean includeBuffer;
//
//    // Getter สำหรับ avgShippingPerPair (computed)
//    public BigDecimal getAvgShippingPerPair() {
//        if (shippingChinaToThaiBath != null && quantity != null && quantity > 0) {
//            return shippingChinaToThaiBath.divide(
//                    BigDecimal.valueOf(quantity), 3, RoundingMode.HALF_UP);
//        }
//        return BigDecimal.ZERO;
//    }
//    // StockLot info (without circular reference)
//    private Long stockLotId;
//    private String lotName;
//}
package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ChinaStockDTO {
    private Long stockItemId;
    private String name;
    private LocalDateTime lotDate;
    private String shopURL;
    private String status;

    // ⭐ Quantity Management - เพิ่มใหม่
    private Integer originalQuantity;    // จำนวนทั้งหมด (ตอนนำเข้า)
    private Integer currentQuantity;     // จำนวนคงเหลือ (ปัจจุบัน)
    private Integer usedQuantity;        // จำนวนที่ใช้ไป
    private BigDecimal usagePercentage;  // เปอร์เซ็นต์ที่ใช้ไป
    private BigDecimal remainingPercentage; // เปอร์เซ็นต์ที่เหลือ

    // ⭐ เก็บ quantity เดิมไว้เพื่อ backward compatibility
    private Integer quantity;

    // Chinese currency fields
    private BigDecimal unitPriceYuan;
    private BigDecimal totalValueYuan;
    private BigDecimal shippingWithinChinaYuan;
    private BigDecimal totalYuan;
    private BigDecimal totalBath;
    private BigDecimal pricePerUnitBath;
    private BigDecimal shippingChinaToThaiBath;
    private BigDecimal finalPricePerPair;
    private BigDecimal exchangeRate;
    private BigDecimal bufferPercentage;
    private Boolean includeBuffer;

    // StockLot info
    private Long stockLotId;
    private String lotName;
}