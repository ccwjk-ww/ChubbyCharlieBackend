package com.example.server.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StockLotDTO {
    private Long stockLotId;
    private String lotName;
    private LocalDateTime importDate;
    private LocalDateTime arrivalDate;
    // ลบ totalShippingBath field ออกแล้ว
    private String status;
    private List<StockItemDTO> items;

    @Data
    public static class StockItemDTO {
        private Long stockItemId;
        private String name;
        private LocalDateTime lotDate;
        private String shopURL;
        private String status;
        private String itemType; // "CHINA" or "THAI"

        // Basic fields for display
        private Integer quantity;
        private java.math.BigDecimal totalValue;
        private java.math.BigDecimal finalPrice;
    }
}