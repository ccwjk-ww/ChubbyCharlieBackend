package com.example.server.dto;

import com.example.server.entity.StockBase;
import com.example.server.entity.ChinaStock;
import com.example.server.entity.ThaiStock;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class StockItemSummaryDto {
    private Long stockItemId;
    private String name;
    private String status;
    private String type; // "CHINA" or "THAI"
    private Integer quantity;
    private BigDecimal finalPrice;
    private BigDecimal totalCost;

    public static StockItemSummaryDto fromEntity(StockBase stockBase) {
        StockItemSummaryDto dto = new StockItemSummaryDto();
        dto.setStockItemId(stockBase.getStockItemId());
        dto.setName(stockBase.getName());
        dto.setStatus(stockBase.getStatus() != null ? stockBase.getStatus().name() : null);
        dto.setFinalPrice(stockBase.calculateFinalPrice());
        dto.setTotalCost(stockBase.calculateTotalCost());

        if (stockBase instanceof ChinaStock) {
            ChinaStock chinaStock = (ChinaStock) stockBase;
            dto.setType("CHINA");
            dto.setQuantity(chinaStock.getQuantity());
        } else if (stockBase instanceof ThaiStock) {
            ThaiStock thaiStock = (ThaiStock) stockBase;
            dto.setType("THAI");
            dto.setQuantity(thaiStock.getQuantity());
        }

        return dto;
    }
}