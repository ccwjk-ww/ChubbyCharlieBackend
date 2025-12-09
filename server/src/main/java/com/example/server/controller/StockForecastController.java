package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.StockBase;
import com.example.server.entity.StockForecast;
import com.example.server.mapper.StockForecastMapper;
import com.example.server.respository.StockBaseRepository;
import com.example.server.service.StockForecastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/stock-forecast")
@CrossOrigin(origins = "*")
public class StockForecastController {

    @Autowired
    private StockForecastService stockForecastService;

    @Autowired
    private StockForecastMapper stockForecastMapper;
    @Autowired
    private StockBaseRepository stockBaseRepository;
    // ============================================
    // การคำนวณ Forecast
    // ============================================

    /**
     * ✅ คำนวณ Stock Forecast ทั้งหมด
     */
    @PostMapping("/calculate-all")
    public ResponseEntity<?> calculateAllForecasts(
            @RequestParam(defaultValue = "90") int analysisBaseDays) {
        try {
            List<StockForecast> forecasts = stockForecastService.calculateAllStockForecasts(analysisBaseDays);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "คำนวณ Stock Forecast สำเร็จ",
                    "totalItems", forecasts.size(),
                    "analysisBaseDays", analysisBaseDays,
                    "forecasts", stockForecastMapper.toStockForecastDTOList(forecasts)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาดในการคำนวณ: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ คำนวณ Stock Forecast สำหรับ Stock Item เดียว
     */
    @PostMapping("/calculate/{stockItemId}")
    public ResponseEntity<?> calculateStockForecast(
            @PathVariable Long stockItemId,
            @RequestParam(defaultValue = "90") int analysisBaseDays) {
        try {
            StockForecast forecast = stockForecastService.calculateStockForecast(stockItemId, analysisBaseDays);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "คำนวณ Stock Forecast สำเร็จ",
                    "forecast", stockForecastMapper.toStockForecastDTO(forecast)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาดในการคำนวณ: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // การดึงข้อมูล Forecast
    // ============================================

    /**
     * ✅ ดึงรายการ Stock ที่ต้องสั่งซื้อเร่งด่วน
     */
    @GetMapping("/urgent")
    public ResponseEntity<List<StockForecastDTO>> getUrgentStockItems() {
        List<StockForecast> urgentItems = stockForecastService.getUrgentStockItems();
        return ResponseEntity.ok(stockForecastMapper.toStockForecastDTOList(urgentItems));
    }

    /**
     * ✅ ดึงรายการ Stock ที่จะหมดในจำนวนวันที่กำหนด
     */
    @GetMapping("/running-out")
    public ResponseEntity<List<StockForecastDTO>> getStockRunningOut(
            @RequestParam(defaultValue = "30") int days) {
        List<StockForecast> runningOutItems = stockForecastService.getStockRunningOutInDays(days);
        return ResponseEntity.ok(stockForecastMapper.toStockForecastDTOList(runningOutItems));
    }

    /**
     * ✅ ดึง Stock Forecast ตาม Stock Type
     */
    @GetMapping("/by-type/{stockType}")
    public ResponseEntity<List<StockForecastDTO>> getForecastsByStockType(@PathVariable String stockType) {
        List<StockForecast> forecasts = stockForecastService.getForecastsByStockType(stockType.toUpperCase());
        return ResponseEntity.ok(stockForecastMapper.toStockForecastDTOList(forecasts));
    }

    /**
     * ✅ ดึงสรุปข้อมูล Stock Forecast
     */
    @GetMapping("/summary")
    public ResponseEntity<StockForecastSummaryDTO> getForecastSummary() {
        Map<String, Object> summary = stockForecastService.getForecastSummary();
        return ResponseEntity.ok(stockForecastMapper.toStockForecastSummaryDTO(summary));
    }

    /**
     * ✅ ดึงคำแนะนำการสั่งซื้อ Stock
     */
    @GetMapping("/order-recommendations")
    public ResponseEntity<StockOrderRecommendationDTO> getOrderRecommendations(
            @RequestParam(defaultValue = "14") int urgentDays,
            @RequestParam(defaultValue = "30") int soonDays) {

        List<StockForecast> urgentItems = stockForecastService.getStockRunningOutInDays(urgentDays);
        List<StockForecast> soonToOrderItems = stockForecastService.getStockRunningOutInDays(soonDays);

        // กรองเอา urgentItems ออกจาก soonToOrderItems
        soonToOrderItems.removeIf(item -> urgentItems.contains(item));

        StockOrderRecommendationDTO recommendations = stockForecastMapper
                .toStockOrderRecommendationDTO(urgentItems, soonToOrderItems);

        return ResponseEntity.ok(recommendations);
    }

    // ============================================
    // การจัดการและการบำรุงรักษา
    // ============================================

    /**
     * ✅ ลบ Forecast เก่า
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<?> cleanupOldForecasts() {
        try {
            stockForecastService.cleanupOldForecasts();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "ลบ Stock Forecast เก่าสำเร็จ"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาดในการลบข้อมูล: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // Dashboard และรายงาน
    // ============================================

    /**
     * ✅ ดึงข้อมูลสำหรับ Dashboard
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getForecastDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        // สรุปข้อมูลหลัก
        Map<String, Object> summary = stockForecastService.getForecastSummary();
        dashboard.put("summary", stockForecastMapper.toStockForecastSummaryDTO(summary));

        // รายการเร่งด่วน
        List<StockForecast> urgentItems = stockForecastService.getUrgentStockItems();
        dashboard.put("urgentItems", stockForecastMapper.toStockForecastDTOList(urgentItems));

        // Top 10 items ที่จะหมดเร็วที่สุด
        List<StockForecast> soonestItems = stockForecastService.getStockRunningOutInDays(60)
                .stream()
                .limit(10)
                .toList();
        dashboard.put("soonestToRunOut", stockForecastMapper.toStockForecastDTOList(soonestItems));

        // แยกตาม Stock Type
        List<StockForecast> chinaForecasts = stockForecastService.getForecastsByStockType("CHINA");
        List<StockForecast> thaiForecasts = stockForecastService.getForecastsByStockType("THAI");

        dashboard.put("chinaStockCount", chinaForecasts.size());
        dashboard.put("thaiStockCount", thaiForecasts.size());

        // คำนวณค่าใช้จ่ายที่ต้องสั่งซื้อ
        double urgentCost = urgentItems.stream()
                .mapToDouble(item -> item.getEstimatedOrderCost() != null ?
                        item.getEstimatedOrderCost().doubleValue() : 0.0)
                .sum();
        dashboard.put("urgentOrderCost", urgentCost);

        return ResponseEntity.ok(dashboard);
    }

    /**
     * ✅ ดึงรายงาน Stock Usage Analysis
     */
    @GetMapping("/usage-analysis")
    public ResponseEntity<Map<String, Object>> getUsageAnalysis(
            @RequestParam(defaultValue = "30") int topItems) {

        Map<String, Object> analysis = new HashMap<>();

        // Top usage items
        List<StockForecast> allForecasts = stockForecastService.getForecastsByStockType("CHINA");
        allForecasts.addAll(stockForecastService.getForecastsByStockType("THAI"));

        List<StockForecast> topUsageItems = allForecasts.stream()
                .sorted((a, b) -> Integer.compare(b.getAverageMonthlyUsage(), a.getAverageMonthlyUsage()))
                .limit(topItems)
                .toList();

        analysis.put("topUsageItems", stockForecastMapper.toStockForecastDTOList(topUsageItems));

        // การใช้งานรวมทั้งระบบ
        int totalDailyUsage = allForecasts.stream()
                .mapToInt(StockForecast::getAverageDailyUsage)
                .sum();
        int totalMonthlyUsage = allForecasts.stream()
                .mapToInt(StockForecast::getAverageMonthlyUsage)
                .sum();

        analysis.put("totalDailyUsage", totalDailyUsage);
        analysis.put("totalMonthlyUsage", totalMonthlyUsage);
        analysis.put("analyzedItems", allForecasts.size());

        return ResponseEntity.ok(analysis);
    }

    /**
     * ✅ Export รายงานในรูปแบบ JSON
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportForecastReport(
            @RequestParam(defaultValue = "ALL") String urgencyLevel,
            @RequestParam(defaultValue = "ALL") String stockType) {

        Map<String, Object> report = new HashMap<>();

        List<StockForecast> forecasts;

        if ("ALL".equals(urgencyLevel)) {
            if ("ALL".equals(stockType)) {
                // ดึงทั้งหมด
                List<StockForecast> chinaForecasts = stockForecastService.getForecastsByStockType("CHINA");
                List<StockForecast> thaiForecasts = stockForecastService.getForecastsByStockType("THAI");
                forecasts = new java.util.ArrayList<>();
                forecasts.addAll(chinaForecasts);
                forecasts.addAll(thaiForecasts);
            } else {
                forecasts = stockForecastService.getForecastsByStockType(stockType);
            }
        } else {
            // Filter ตาม urgency level
            forecasts = stockForecastService.getUrgentStockItems(); // TODO: ต้องเพิ่ม method filter ตาม urgency level
        }

        report.put("exportDate", java.time.LocalDateTime.now());
        report.put("filters", Map.of(
                "urgencyLevel", urgencyLevel,
                "stockType", stockType
        ));
        report.put("totalItems", forecasts.size());
        report.put("forecasts", stockForecastMapper.toStockForecastDTOList(forecasts));

        // สรุปข้อมูล
        Map<String, Object> summary = stockForecastService.getForecastSummary();
        report.put("summary", summary);

        return ResponseEntity.ok(report);
    }
    /**
     * ⭐ คำนวณ Forecast ด้วย AI
     */
    @PostMapping("/calculate-ai/{stockItemId}")
    public ResponseEntity<?> calculateStockForecastWithAI(
            @PathVariable Long stockItemId,
            @RequestParam(defaultValue = "90") int analysisBaseDays) {
        try {
            StockForecast forecast = stockForecastService.calculateStockForecastWithAI(
                    stockItemId, analysisBaseDays);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "🤖 คำนวณ Stock Forecast ด้วย AI สำเร็จ",
                    "forecast", stockForecastMapper.toStockForecastDTO(forecast),
                    "powered_by", "Google Gemini AI"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }

    /**
     * ⭐ คำนวณ Forecast ทั้งหมดด้วย AI
     */
    @PostMapping("/calculate-all-ai")
    public ResponseEntity<?> calculateAllForecastsWithAI(
            @RequestParam(defaultValue = "90") int analysisBaseDays) {
        try {
            List<StockBase> allStockItems = stockBaseRepository.findAll();
            List<StockForecast> forecasts = new ArrayList<>();

            int successCount = 0;
            int errorCount = 0;

            for (StockBase stockItem : allStockItems) {
                try {
                    StockForecast forecast = stockForecastService.calculateStockForecastWithAI(
                            stockItem.getStockItemId(), analysisBaseDays);
                    forecasts.add(forecast);
                    successCount++;
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Error for Stock ID " + stockItem.getStockItemId() + ": " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "🤖 คำนวณ Stock Forecast ด้วย AI เสร็จสิ้น",
                    "totalItems", allStockItems.size(),
                    "successCount", successCount,
                    "errorCount", errorCount,
                    "analysisBaseDays", analysisBaseDays,
                    "forecasts", stockForecastMapper.toStockForecastDTOList(forecasts),
                    "powered_by", "Google Gemini AI"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }

    /**
     * ⭐ ดึงคำแนะนำการสั่งซื้อแบบอัจฉริยะ
     */
    @GetMapping("/ai-recommendations")
    public ResponseEntity<?> getAIOrderRecommendations() {
        try {
            // ดึง forecasts ที่มี AI analysis
            List<StockForecast> urgentItems = stockForecastService.getUrgentStockItems();

            Map<String, Object> recommendations = new HashMap<>();
            recommendations.put("urgentCount", urgentItems.size());
            recommendations.put("urgentItems", stockForecastMapper.toStockForecastDTOList(urgentItems));

            // คำนวณงบประมาณที่ต้องการ
            BigDecimal totalBudget = urgentItems.stream()
                    .map(StockForecast::getEstimatedOrderCost)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            recommendations.put("totalBudgetNeeded", totalBudget);
            recommendations.put("generatedAt", LocalDateTime.now());
            recommendations.put("powered_by", "Google Gemini AI");

            return ResponseEntity.ok(recommendations);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }
}