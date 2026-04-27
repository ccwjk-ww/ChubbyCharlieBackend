package com.example.server.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

@Data
public class StockLotDTO {
    private Long stockLotId;
    private String lotName;
    private LocalDateTime importDate;
    private LocalDateTime arrivalDate;
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

        // Basic fields
        private Integer quantity;
        private BigDecimal totalValue;
        private BigDecimal finalPrice;

        // VAT fields
        private Boolean includeVat;
        private BigDecimal vatPercentage;
        private BigDecimal totalValueBeforeVat;
        private BigDecimal vatAmount;
        private BigDecimal totalValueWithVat;
        private BigDecimal finalPriceWithVat;

        // ChinaStock-specific fields
        private BigDecimal totalBath;
        private BigDecimal shippingChinaToThaiBath;
        private BigDecimal finalPricePerPair;

        // ⭐ NEW: Defective fields
        private Integer defectiveQuantity;
        private BigDecimal defectiveValue;
    }
}