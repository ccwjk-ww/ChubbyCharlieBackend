package com.example.server.controller;

import com.example.server.service.StockForecastScheduledService;
import com.example.server.service.StockForecastService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stock-forecast/admin")
@CrossOrigin(origins = "*")
public class StockForecastAdminController {

    @Autowired
    private StockForecastService stockForecastService;

    @Autowired
    private StockForecastScheduledService scheduledService;

    /**
     * ✅ Manual trigger สำหรับคำนวณ Forecast ทันที
     */
    @PostMapping("/trigger-calculation")
    public ResponseEntity<?> triggerCalculation() {
        try {
            scheduledService.triggerImmediateForecastCalculation();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "เริ่มคำนวณ Stock Forecast แล้ว กรุณาตรวจสอบ console log"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ Manual trigger สำหรับสร้างรายงานทันที
     */
    @PostMapping("/trigger-report")
    public ResponseEntity<?> triggerReport() {
        try {
            scheduledService.triggerImmediateSummaryReport();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "สร้างรายงานสรุปแล้ว กรุณาตรวจสอบ console log"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ ทดสอบการคำนวณสำหรับ Stock Item เดียว
     */
    @PostMapping("/test-single/{stockItemId}")
    public ResponseEntity<?> testSingleCalculation(@PathVariable Long stockItemId) {
        try {
            var forecast = stockForecastService.calculateStockForecast(stockItemId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "ทดสอบการคำนวณสำเร็จ",
                    "stockItemId", stockItemId,
                    "stockItemName", forecast.getStockItemName(),
                    "currentStock", forecast.getCurrentStock(),
                    "averageDailyUsage", forecast.getAverageDailyUsage(),
                    "daysUntilStockOut", forecast.getDaysUntilStockOut(),
                    "urgencyLevel", forecast.getUrgencyLevel().name(),
                    "recommendedOrderQuantity", forecast.getRecommendedOrderQuantity(),
                    "estimatedOrderCost", forecast.getEstimatedOrderCost()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาดในการทดสอบ: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ ดูข้อมูล Health Check ของระบบ Forecast
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        try {
            var summary = stockForecastService.getForecastSummary();
            var urgentItems = stockForecastService.getUrgentStockItems();

            return ResponseEntity.ok(Map.of(
                    "systemStatus", "HEALTHY",
                    "totalForecasts", summary.get("totalItems"),
                    "urgentItems", urgentItems.size(),
                    "lastChecked", java.time.LocalDateTime.now(),
                    "features", Map.of(
                            "automaticCalculation", "ENABLED",
                            "scheduledReports", "ENABLED",
                            "cleanup", "ENABLED"
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "systemStatus", "ERROR",
                    "error", e.getMessage(),
                    "lastChecked", java.time.LocalDateTime.now()
            ));
        }
    }

    /**
     * ✅ ล้างข้อมูล Forecast ทั้งหมด (ใช้เฉพาะ Development)
     */
    @DeleteMapping("/reset-all")
    public ResponseEntity<?> resetAllForecasts() {
        try {
            // ลบข้อมูลเก่าทั้งหมด (ไม่จำกัด 30 วัน)
            stockForecastService.cleanupOldForecasts();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "ลบข้อมูล Stock Forecast ทั้งหมดแล้ว",
                    "warning", "การกระทำนี้ไม่สามารถย้อนกลับได้"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }
}