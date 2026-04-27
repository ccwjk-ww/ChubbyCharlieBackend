//package com.example.server.dto;
//
//import lombok.Data;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.List;
//
//@Data
//public class StockForecastDTO {
//    private Long forecastId;
//    private Long stockItemId;
//    private String stockItemName;
//    private String stockType;
//
//    // ข้อมูล Stock ปัจจุบัน
//    private Integer currentStock;
//    private BigDecimal currentStockValue;
//
//    // การวิเคราะห์ความต้องการ
//    private Integer averageDailyUsage;
//    private Integer averageWeeklyUsage;
//    private Integer averageMonthlyUsage;
//
//    // การคาดการณ์
//    private Integer daysUntilStockOut;
//    private LocalDateTime estimatedStockOutDate;
//
//    // คำแนะนำการสั่งซื้อ
//    private Integer recommendedOrderQuantity;
//    private BigDecimal estimatedOrderCost;
//
//    // สถานะความเร่งด่วน
//    private String urgencyLevel;
//    private String urgencyDescription;
//    private String recommendations;
//
//    // ข้อมูลการวิเคราะห์
//    private Integer analysisBasedOnDays;
//    private LocalDateTime lastCalculatedDate;
//    private Integer safetyStockDays;
//    private Integer leadTimeDays;
//}
//
//@Data
//class StockUsageAnalysisDTO {
//    private Long stockItemId;
//    private String stockItemName;
//    private String stockType;
//    private Integer currentStock;
//
//    // ข้อมูลการใช้งานใน periods ต่างๆ
//    private UsagePeriodDTO last7Days;
//    private UsagePeriodDTO last30Days;
//    private UsagePeriodDTO last90Days;
//
//    // แนวโน้มการใช้งาน
//    private String usageTrend; // "INCREASING", "DECREASING", "STABLE"
//    private Double trendPercentage;
//
//    @Data
//    public static class UsagePeriodDTO {
//        private Integer totalUsed;
//        private Integer averageDailyUsage;
//        private Integer maxDailyUsage;
//        private Integer minDailyUsage;
//        private LocalDateTime periodStart;
//        private LocalDateTime periodEnd;
//    }
//}
//
//@Data
//class ForecastConfigurationDTO {
//    private Integer defaultSafetyStockDays;
//    private Integer defaultLeadTimeDays;
//    private Integer analysisBaseDays;
//    private Boolean autoCalculateEnabled;
//    private String calculationFrequency; // "DAILY", "WEEKLY"
//    private LocalDateTime lastGlobalCalculation;
//}
//
//@Data
//class StockForecastHistoryDTO {
//    private Long stockItemId;
//    private String stockItemName;
//    private List<ForecastPointDTO> forecastHistory;
//
//    @Data
//    public static class ForecastPointDTO {
//        private LocalDateTime calculatedDate;
//        private Integer currentStock;
//        private Integer daysUntilStockOut;
//        private Integer averageDailyUsage;
//        private String urgencyLevel;
//    }
//}
package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * ✅ Enhanced StockForecastDTO with Monthly Analysis
 * รองรับการวิเคราะห์รายเดือน 6 เดือนย้อนหลัง
 */
@Data
public class StockForecastDTO {
    private Long forecastId;
    private Long stockItemId;
    private String stockItemName;
    private String stockType;

    // ข้อมูล Stock ปัจจุบัน
    private Integer currentStock;
    private BigDecimal currentStockValue;

    // ⭐ NEW: การวิเคราะห์รายเดือน (6 เดือนย้อนหลัง)
    private List<MonthlyUsageDTO> monthlyUsageHistory; // ยอดใช้แต่ละเดือน
    private Map<String, Integer> last6MonthsUsage; // {"2024-12": 500, "2025-01": 600, ...}

    // ⭐ NEW: การคาดการณ์เดือนถัดไป
    private Integer predictedNextMonthUsage; // คาดการณ์เดือนหน้าจะใช้เท่าไหร่
    private YearMonth nextMonth; // เดือนที่คาดการณ์
    private String forecastMethod; // "AVERAGE", "LINEAR_REGRESSION", "WEIGHTED_AVERAGE"
    private Double forecastConfidence; // ความมั่นใจ 0-100%

    // ⭐ NEW: Trend Analysis
    private String usageTrend; // "INCREASING", "DECREASING", "STABLE"
    private Double trendPercentage; // เปอร์เซ็นต์การเปลี่ยนแปลง
    private Integer trendDirection; // 1 = เพิ่มขึ้น, 0 = คงที่, -1 = ลดลง

    // การวิเคราะห์ความต้องการ (เดิม - คงไว้เพื่อ backward compatibility)
    private Integer averageDailyUsage;
    private Integer averageWeeklyUsage;
    private Integer averageMonthlyUsage; // ค่าเฉลี่ยจาก 6 เดือน

    // การคาดการณ์ (ปรับให้อิงจากเดือนถัดไป)
    private Integer daysUntilStockOut;
    private LocalDateTime estimatedStockOutDate;

    // ⭐ NEW: คำแนะนำการสั่งซื้อสำหรับเดือนถัดไป
    private Integer recommendedOrderQuantity; // แนะนำสั่งซื้อเท่าไหร่
    private Integer recommendedOrderForNextMonth; // สำหรับเดือนหน้าโดยเฉพาะ
    private BigDecimal estimatedOrderCost;

    // ⭐ NEW: การวิเคราะห์เพิ่มเติม
    private Integer maxMonthlyUsage; // เดือนที่ใช้มากที่สุด
    private Integer minMonthlyUsage; // เดือนที่ใช้น้อยที่สุด
    private Double usageStandardDeviation; // ความผันผวนของการใช้งาน

    // สถานะความเร่งด่วน
    private String urgencyLevel;
    private String urgencyDescription;
    private String recommendations;

    // ข้อมูลการวิเคราะห์
    private Integer analysisBasedOnDays; // จำนวนวันที่วิเคราะห์ (180 วัน = 6 เดือน)
    private Integer analysisBasedOnMonths; // จำนวนเดือนที่วิเคราะห์ (6 เดือน)
    private LocalDateTime lastCalculatedDate;
    private Integer safetyStockDays;
    private Integer leadTimeDays;
}

/**
 * ✅ Monthly Usage Data Transfer Object
 * เก็บข้อมูลการใช้งานในแต่ละเดือน
 */
@Data
class MonthlyUsageDTO {
    private YearMonth month; // ปี-เดือน (เช่น 2025-01)
    private Integer totalUsage; // ยอดรวมที่ใช้ในเดือนนั้น
    private Integer totalOrderQuantity; // จำนวน Order ทั้งหมด
    private BigDecimal totalRevenue; // รายได้รวมในเดือนนั้น
    private Integer averageDailyUsage; // เฉลี่ยต่อวันในเดือนนั้น
    private Integer daysInMonth; // จำนวนวันที่มีข้อมูล

    // ⭐ Detailed breakdown
    private Integer weekdayUsage; // ใช้ในวันจันทร์-ศุกร์
    private Integer weekendUsage; // ใช้ในวันเสาร์-อาทิตย์
    private Integer peakDayUsage; // วันที่ใช้มากที่สุด
    private String peakDate; // วันที่ใช้มากที่สุด
}

/**
 * ✅ Stock Usage Analysis DTO
 * การวิเคราะห์การใช้งาน Stock แบบละเอียด
 */
@Data
class StockUsageAnalysisDTO {
    private Long stockItemId;
    private String stockItemName;
    private String stockType;
    private Integer currentStock;

    // ⭐ NEW: Monthly analysis
    private List<MonthlyUsageDTO> monthlyBreakdown;
    private MonthlyComparisonDTO monthComparison;

    // ข้อมูลการใช้งานใน periods ต่างๆ
    private UsagePeriodDTO last7Days;
    private UsagePeriodDTO last30Days;
    private UsagePeriodDTO last90Days;
    private UsagePeriodDTO last180Days; // ⭐ NEW: 6 เดือน

    // แนวโน้มการใช้งาน
    private String usageTrend;
    private Double trendPercentage;

    // ⭐ NEW: Seasonal patterns
    private Map<Integer, Integer> monthlyAverages; // เฉลี่ยแต่ละเดือนในปี
    private String seasonalPattern; // "HIGH_SEASON", "LOW_SEASON", "NORMAL"
    private Integer seasonalityIndex; // ค่า index ของฤดูกาล

    @Data
    public static class UsagePeriodDTO {
        private Integer totalUsed;
        private Integer averageDailyUsage;
        private Integer maxDailyUsage;
        private Integer minDailyUsage;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }

    @Data
    public static class MonthlyComparisonDTO {
        private Integer thisMonthUsage; // เดือนนี้
        private Integer lastMonthUsage; // เดือนที่แล้ว
        private Integer sameMonthLastYearUsage; // เดือนเดียวกันปีที่แล้ว
        private Double monthOverMonthChange; // % เปลี่ยนแปลงเทียบเดือนก่อน
        private Double yearOverYearChange; // % เปลี่ยนแปลงเทียบปีที่แล้ว
    }
}

/**
 * ✅ Forecast Configuration DTO
 */
@Data
class ForecastConfigurationDTO {
    private Integer defaultSafetyStockDays;
    private Integer defaultLeadTimeDays;
    private Integer analysisBaseDays;
    private Integer analysisBaseMonths; // ⭐ NEW: จำนวนเดือนที่วิเคราะห์
    private Boolean autoCalculateEnabled;
    private String calculationFrequency;
    private LocalDateTime lastGlobalCalculation;

    // ⭐ NEW: Forecast settings
    private String defaultForecastMethod; // "AVERAGE", "LINEAR_REGRESSION", "WEIGHTED_AVERAGE"
    private Boolean enableSeasonalAdjustment; // เปิด/ปิดการปรับตามฤดูกาล
    private Double minimumConfidenceThreshold; // ความมั่นใจขั้นต่ำ
}

/**
 * ✅ Stock Forecast History DTO
 * เก็บประวัติการคาดการณ์
 */
@Data
class StockForecastHistoryDTO {
    private Long stockItemId;
    private String stockItemName;
    private List<ForecastPointDTO> forecastHistory;

    // ⭐ NEW: Accuracy tracking
    private Double averageForecastAccuracy; // ความแม่นยำเฉลี่ย
    private List<AccuracyPointDTO> accuracyHistory; // ประวัติความแม่นยำ

    @Data
    public static class ForecastPointDTO {
        private LocalDateTime calculatedDate;
        private Integer currentStock;
        private Integer daysUntilStockOut;
        private Integer averageDailyUsage;
        private String urgencyLevel;

        // ⭐ NEW
        private Integer predictedUsage; // คาดการณ์
        private Integer actualUsage; // จริง (ถ้ามี)
        private Double accuracy; // ความแม่นยำ %
    }

    @Data
    public static class AccuracyPointDTO {
        private YearMonth month;
        private Integer predicted;
        private Integer actual;
        private Double accuracy; // %
        private String status; // "ACCURATE", "OVERESTIMATE", "UNDERESTIMATE"
    }
}