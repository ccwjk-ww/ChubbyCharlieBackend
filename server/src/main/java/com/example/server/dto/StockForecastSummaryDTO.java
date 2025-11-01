package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class StockForecastSummaryDTO {
    private Long totalItems;
    private Long criticalItems;
    private Long highUrgencyItems;
    private Long mediumUrgencyItems;
    private Long lowUrgencyItems;
    private BigDecimal totalEstimatedCost;
    private BigDecimal criticalItemsCost;
    private BigDecimal highUrgencyItemsCost;
    private LocalDateTime lastUpdated;
}
