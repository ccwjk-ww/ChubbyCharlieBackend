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

    // ⭐ grandTotalValue จะเก็บค่า Grand Total (รวม Buffer)
    private BigDecimal grandTotalValue;
}