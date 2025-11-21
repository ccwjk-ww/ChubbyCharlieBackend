package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SystemSummaryDTO {
    private Integer totalLots;
    private Integer totalItems;
    private Integer totalChinaItems;
    private Integer totalThaiItems;
    private Integer activeItems;

    // ⭐ totalInventoryValue จะเก็บค่า Grand Total ทั้งหมด
    private BigDecimal totalInventoryValue;
}