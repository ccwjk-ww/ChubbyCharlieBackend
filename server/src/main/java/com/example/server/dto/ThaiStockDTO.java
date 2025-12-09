//package com.example.server.dto;
//
//import lombok.Data;
//import java.time.LocalDateTime;
//import java.math.BigDecimal;
//
//@Data
//public class ThaiStockDTO {
//    private Long stockItemId;
//    private String name;
//    private LocalDateTime lotDate;
//    private String shopURL;
//    private String status;
//
//    // Thai-specific fields
//    private Integer quantity;
//    private BigDecimal priceTotal;
//    private BigDecimal shippingCost;
//    private BigDecimal pricePerUnit;
//    private BigDecimal pricePerUnitWithShipping;
//    // ⭐ เพิ่ม buffer fields
//    private BigDecimal bufferPercentage;
//    private Boolean includeBuffer;
//    // StockLot info (without circular reference)
//    private Long stockLotId;
//    private String lotName;
//    private BigDecimal totalCost;
//}
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

    // ⭐ Quantity Management - เพิ่มใหม่
    private Integer originalQuantity;    // จำนวนทั้งหมด (ตอนนำเข้า)
    private Integer currentQuantity;     // จำนวนคงเหลือ (ปัจจุบัน)
    private Integer usedQuantity;        // จำนวนที่ใช้ไป
    private BigDecimal usagePercentage;  // เปอร์เซ็นต์ที่ใช้ไป
    private BigDecimal remainingPercentage; // เปอร์เซ็นต์ที่เหลือ

    // ⭐ เก็บ quantity เดิมไว้เพื่อ backward compatibility
    private Integer quantity;

    // Thai-specific fields
    private BigDecimal priceTotal;
    private BigDecimal shippingCost;
    private BigDecimal pricePerUnit;
    private BigDecimal pricePerUnitWithShipping;
    private BigDecimal bufferPercentage;
    private Boolean includeBuffer;

    // StockLot info
    private Long stockLotId;
    private String lotName;
    private BigDecimal totalCost;
}