package com.example.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class StockForecastScheduledService {

    @Autowired
    private StockForecastService stockForecastService;

    /**
     * ✅ คำนวณ Stock Forecast อัตโนมัติทุกวันเวลา 02:00
     * ใช้ @Scheduled annotation สำหรับรันอัตโนมัติ
     */
    @Scheduled(cron = "0 0 2 * * *") // ทุกวันเวลา 02:00
    public void scheduledForecastCalculation() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            System.out.println("🕐 [" + currentTime + "] เริ่มคำนวณ Stock Forecast อัตโนมัติ...");

            // คำนวณ forecast ทั้งหมด
            var forecasts = stockForecastService.calculateAllStockForecasts();

            System.out.printf("✅ [%s] คำนวณ Stock Forecast สำเร็จ: %d items\n",
                    currentTime, forecasts.size());

            // ตรวจสอบรายการเร่งด่วน
            var urgentItems = stockForecastService.getUrgentStockItems();
            if (!urgentItems.isEmpty()) {
                System.out.printf("⚠️ [%s] พบ Stock เร่งด่วน %d รายการ:\n",
                        currentTime, urgentItems.size());

                urgentItems.forEach(item -> {
                    System.out.printf("   - %s: เหลือ %d วัน (%s)\n",
                            item.getStockItemName(),
                            item.getDaysUntilStockOut(),
                            item.getUrgencyLevel().getDescription());
                });
            }

        } catch (Exception e) {
            System.err.printf("❌ [%s] เกิดข้อผิดพลาดในการคำนวณ Stock Forecast: %s\n",
                    currentTime, e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ ลบข้อมูล Forecast เก่าทุกสัปดาห์ในวันอาทิตย์เวลา 03:00
     */
    @Scheduled(cron = "0 0 3 * * SUN") // ทุกวันอาทิตย์เวลา 03:00
    public void scheduledCleanupOldForecasts() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            System.out.println("🧹 [" + currentTime + "] เริ่มลบ Stock Forecast เก่า...");

            stockForecastService.cleanupOldForecasts();

            System.out.println("✅ [" + currentTime + "] ลบ Stock Forecast เก่าสำเร็จ");

        } catch (Exception e) {
            System.err.printf("❌ [%s] เกิดข้อผิดพลาดในการลบข้อมูลเก่า: %s\n",
                    currentTime, e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ รายงานสรุป Stock Forecast ทุกวันเวลา 08:00 (เวลาทำงาน)
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI") // วันจันทร์-ศุกร์ เวลา 08:00
    public void scheduledForecastSummaryReport() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        try {
            System.out.println("📊 [" + currentTime + "] สร้างรายงานสรุป Stock Forecast...");

            // ดึงข้อมูลสรุป
            var summary = stockForecastService.getForecastSummary();
            var urgentItems = stockForecastService.getUrgentStockItems();
            var runningOutSoon = stockForecastService.getStockRunningOutInDays(14);

            // แสดงรายงานใน console (ในการใช้งานจริงอาจส่งอีเมลหรือ notification)
            System.out.println("📈 รายงานสรุป Stock Forecast ประจำวัน");
            System.out.println("═══════════════════════════════════════");
            System.out.printf("📦 Stock Items ทั้งหมด: %s\n", summary.get("totalItems"));
            System.out.printf("🚨 วิกฤต (Critical): %s\n", summary.get("criticalItems"));
            System.out.printf("⚠️ เร่งด่วน (High): %s\n", summary.get("highUrgencyItems"));
            System.out.printf("📋 ปานกลาง (Medium): %s\n", summary.get("mediumUrgencyItems"));
            System.out.printf("✅ ปกติ (Low): %s\n", summary.get("lowUrgencyItems"));
            System.out.println("═══════════════════════════════════════");

            if (!urgentItems.isEmpty()) {
                System.out.println("🚨 รายการที่ต้องสั่งซื้อเร่งด่วน:");
                urgentItems.stream().limit(5).forEach(item -> {
                    System.out.printf("   • %s: เหลือ %d วัน (ต้นทุน: ฿%.2f)\n",
                            item.getStockItemName(),
                            item.getDaysUntilStockOut(),
                            item.getEstimatedOrderCost());
                });

                if (urgentItems.size() > 5) {
                    System.out.printf("   ... และอีก %d รายการ\n", urgentItems.size() - 5);
                }
            }

            if (!runningOutSoon.isEmpty()) {
                System.out.println("📅 รายการที่จะหมดใน 14 วัน:");
                runningOutSoon.stream().limit(5).forEach(item -> {
                    System.out.printf("   • %s: เหลือ %d วัน\n",
                            item.getStockItemName(),
                            item.getDaysUntilStockOut());
                });

                if (runningOutSoon.size() > 5) {
                    System.out.printf("   ... และอีก %d รายการ\n", runningOutSoon.size() - 5);
                }
            }

            System.out.println("═══════════════════════════════════════");

        } catch (Exception e) {
            System.err.printf("❌ [%s] เกิดข้อผิดพลาดในการสร้างรายงาน: %s\n",
                    currentTime, e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Manual trigger สำหรับคำนวณทันที (สำหรับ testing หรือใช้งานด่วน)
     */
    public void triggerImmediateForecastCalculation() {
        System.out.println("🔄 Manual trigger: เริ่มคำนวณ Stock Forecast ทันที...");
        scheduledForecastCalculation();
    }

    /**
     * ✅ Manual trigger สำหรับสร้างรายงานทันที
     */
    public void triggerImmediateSummaryReport() {
        System.out.println("📊 Manual trigger: สร้างรายงานสรุป Stock Forecast ทันที...");
        scheduledForecastSummaryReport();
    }
}