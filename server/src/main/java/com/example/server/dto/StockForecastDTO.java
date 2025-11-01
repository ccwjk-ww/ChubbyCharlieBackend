package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StockForecastDTO {
    private Long forecastId;
    private Long stockItemId;
    private String stockItemName;
    private String stockType;

    // ข้อมูล Stock ปัจจุบัน
    private Integer currentStock;
    private BigDecimal currentStockValue;

    // การวิเคราะห์ความต้องการ
    private Integer averageDailyUsage;
    private Integer averageWeeklyUsage;
    private Integer averageMonthlyUsage;

    // การคาดการณ์
    private Integer daysUntilStockOut;
    private LocalDateTime estimatedStockOutDate;

    // คำแนะนำการสั่งซื้อ
    private Integer recommendedOrderQuantity;
    private BigDecimal estimatedOrderCost;

    // สถานะความเร่งด่วน
    private String urgencyLevel;
    private String urgencyDescription;
    private String recommendations;

    // ข้อมูลการวิเคราะห์
    private Integer analysisBasedOnDays;
    private LocalDateTime lastCalculatedDate;
    private Integer safetyStockDays;
    private Integer leadTimeDays;
}

@Data
class StockUsageAnalysisDTO {
    private Long stockItemId;
    private String stockItemName;
    private String stockType;
    private Integer currentStock;

    // ข้อมูลการใช้งานใน periods ต่างๆ
    private UsagePeriodDTO last7Days;
    private UsagePeriodDTO last30Days;
    private UsagePeriodDTO last90Days;

    // แนวโน้มการใช้งาน
    private String usageTrend; // "INCREASING", "DECREASING", "STABLE"
    private Double trendPercentage;

    @Data
    public static class UsagePeriodDTO {
        private Integer totalUsed;
        private Integer averageDailyUsage;
        private Integer maxDailyUsage;
        private Integer minDailyUsage;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }
}

@Data
class ForecastConfigurationDTO {
    private Integer defaultSafetyStockDays;
    private Integer defaultLeadTimeDays;
    private Integer analysisBaseDays;
    private Boolean autoCalculateEnabled;
    private String calculationFrequency; // "DAILY", "WEEKLY"
    private LocalDateTime lastGlobalCalculation;
}

@Data
class StockForecastHistoryDTO {
    private Long stockItemId;
    private String stockItemName;
    private List<ForecastPointDTO> forecastHistory;

    @Data
    public static class ForecastPointDTO {
        private LocalDateTime calculatedDate;
        private Integer currentStock;
        private Integer daysUntilStockOut;
        private Integer averageDailyUsage;
        private String urgencyLevel;
    }
}