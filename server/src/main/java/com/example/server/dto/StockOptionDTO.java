package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StockOptionDTO {
    private Long stockItemId;
    private String name;
    private String type; // "CHINA" or "THAI"
    private BigDecimal unitCost;
    private String status;

    // ⭐ เพิ่ม lotName
    private String lotName;

    // ⭐ เพิ่ม lotId (optional - ถ้าต้องการ)
    private Long stockLotId;
}