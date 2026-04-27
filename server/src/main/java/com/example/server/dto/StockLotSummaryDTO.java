package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StockLotSummaryDTO {
    private Long stockLotId;
    private String lotName;
    private Integer totalItemCount;
    private Integer chinaItemCount;
    private Integer thaiItemCount;

    // ⭐ แยก 3 ค่า เหมือนหน้า stock-lot-detail
    private BigDecimal totalCostBeforeVat;  // ยอดรวมก่อน VAT
    private BigDecimal totalVatAmount;      // VAT รวมทั้ง Lot
    private BigDecimal totalCostWithVat;    // ยอดรวมหลัง VAT (ใช้แสดง Total Value)

    // ⭐ backward compat — grandTotalValue = totalCostWithVat
    public BigDecimal getGrandTotalValue() {
        return totalCostWithVat;
    }

    public void setGrandTotalValue(BigDecimal value) {
        this.totalCostWithVat = value;
    }
}