package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class StockForecastService {

    @Autowired
    private StockForecastRepository stockForecastRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Autowired
    private StockBaseRepository stockBaseRepository;

    @Autowired
    private OrderRepository orderRepository;

    // Configuration defaults
    private static final int DEFAULT_SAFETY_STOCK_DAYS = 7;
    private static final int DEFAULT_LEAD_TIME_DAYS = 14;
    private static final int DEFAULT_ANALYSIS_DAYS = 90;

    /**
     * ✅ คำนวณ Stock Forecast สำหรับ Stock Item ทั้งหมด
     */
    @Transactional
    public List<StockForecast> calculateAllStockForecasts() {
        return calculateAllStockForecasts(DEFAULT_ANALYSIS_DAYS);
    }

    /**
     * ✅ คำนวณ Stock Forecast สำหรับ Stock Item ทั้งหมด โดยกำหนดช่วงเวลาวิเคราะห์
     */
    @Transactional
    public List<StockForecast> calculateAllStockForecasts(int analysisBaseDays) {
        System.out.println("🔄 เริ่มคำนวณ Stock Forecast สำหรับ Stock Items ทั้งหมด...");

        List<StockBase> allStockItems = stockBaseRepository.findAll();
        List<StockForecast> forecasts = new ArrayList<>();

        int processed = 0;
        int successCount = 0;
        int errorCount = 0;

        for (StockBase stockItem : allStockItems) {
            try {
                StockForecast forecast = calculateStockForecast(stockItem.getStockItemId(), analysisBaseDays);
                if (forecast != null) {
                    forecasts.add(forecast);
                    successCount++;
                }
                processed++;

                if (processed % 10 == 0) {
                    System.out.printf("📊 ประมวลผลแล้ว %d/%d items (สำเร็จ: %d, ข้อผิดพลาด: %d)\n",
                            processed, allStockItems.size(), successCount, errorCount);
                }

            } catch (Exception e) {
                errorCount++;
                System.err.printf("❌ Error calculating forecast for Stock ID %d: %s\n",
                        stockItem.getStockItemId(), e.getMessage());
                e.printStackTrace(); // เพิ่ม stack trace เพื่อ debug
            }
        }

        System.out.printf("✅ เสร็จสิ้นการคำนวณ: สำเร็จ %d items, ข้อผิดพลาด %d items\n",
                successCount, errorCount);

        return forecasts;
    }

    /**
     * ✅ คำนวณ Stock Forecast สำหรับ Stock Item เดียว
     */
    @Transactional
    public StockForecast calculateStockForecast(Long stockItemId) {
        return calculateStockForecast(stockItemId, DEFAULT_ANALYSIS_DAYS);
    }

    /**
     * ✅ คำนวณ Stock Forecast สำหรับ Stock Item เดียว โดยกำหนดช่วงเวลาวิเคราะห์
     */
    @Transactional
    public StockForecast calculateStockForecast(Long stockItemId, int analysisBaseDays) {
        // 1. โหลด Stock Item
        StockBase stockItem = stockBaseRepository.findById(stockItemId)
                .orElseThrow(() -> new RuntimeException("Stock Item not found: " + stockItemId));

        // 2. วิเคราะห์การใช้งานจาก Order ย้อนหลัง
        LocalDateTime analysisStartDate = LocalDateTime.now().minusDays(analysisBaseDays);
        StockUsageAnalysis usageAnalysis = analyzeStockUsage(stockItemId, analysisStartDate);

        // 3. สร้าง Forecast
        StockForecast forecast = createOrUpdateForecast(stockItem, usageAnalysis, analysisBaseDays);

        // 4. บันทึกลงฐานข้อมูล
        return stockForecastRepository.save(forecast);
    }

    /**
     * ✅ วิเคราะห์การใช้งาน Stock จากข้อมูล Order ย้อนหลัง
     */
    private StockUsageAnalysis analyzeStockUsage(Long stockItemId, LocalDateTime analysisStartDate) {
        // หา Products ที่ใช้ Stock Item นี้
        List<Product> productsUsingStock = productIngredientRepository.findProductsUsingStockItem(stockItemId);

        if (productsUsingStock.isEmpty()) {
            return new StockUsageAnalysis(); // ไม่มีการใช้งาน
        }

        // รวบรวมข้อมูลการใช้งานจาก Order Items ทั้งหมด
        Map<LocalDateTime, Integer> dailyUsage = new TreeMap<>();
        int totalUsageInPeriod = 0;

        for (Product product : productsUsingStock) {
            // หา Order Items ของ Product นี้ในช่วงเวลาที่กำหนด
            List<OrderItem> orderItems = orderItemRepository.findByProductProductId(product.getProductId());

            for (OrderItem orderItem : orderItems) {
                Order order = orderItem.getOrder();

                // เฉพาะ Order ที่ไม่ถูกยกเลิกและอยู่ในช่วงเวลาที่วิเคราะห์
                if (order != null &&
                        order.getOrderDate() != null &&
                        order.getOrderDate().isAfter(analysisStartDate) &&
                        order.getStatus() != Order.OrderStatus.CANCELLED &&
                        order.getStatus() != Order.OrderStatus.RETURNED) {

                    // คำนวณจำนวน Stock ที่ใช้สำหรับ Order Item นี้
                    int stockUsed = calculateStockUsageForOrderItem(stockItemId, orderItem);

                    if (stockUsed > 0) {
                        LocalDateTime orderDate = order.getOrderDate().toLocalDate().atStartOfDay();
                        dailyUsage.merge(orderDate, stockUsed, Integer::sum);
                        totalUsageInPeriod += stockUsed;
                    }
                }
            }
        }

        return new StockUsageAnalysis(dailyUsage, totalUsageInPeriod, analysisStartDate);
    }

    /**
     * ✅ คำนวณจำนวน Stock ที่ใช้สำหรับ Order Item หนึ่งรายการ
     */
    private int calculateStockUsageForOrderItem(Long stockItemId, OrderItem orderItem) {
        // หา Product Ingredients ที่ใช้ Stock Item นี้
        List<ProductIngredient> ingredients = productIngredientRepository.findByStockItemStockItemId(stockItemId);

        for (ProductIngredient ingredient : ingredients) {
            if (ingredient.getProduct().getProductId().equals(orderItem.getProduct().getProductId())) {
                // คำนวณจำนวนที่ใช้ = จำนวน Order × จำนวนที่ต้องใช้ต่อชิ้น
                BigDecimal requiredQuantity = ingredient.getRequiredQuantity();
                Integer orderQuantity = orderItem.getQuantity();

                if (requiredQuantity != null && orderQuantity != null) {
                    return (int) (orderQuantity * requiredQuantity.doubleValue());
                }
            }
        }
        return 0;
    }

    /**
     * ✅ สร้างหรืออัพเดท Stock Forecast
     * 🔧 FIX: เพิ่มการเช็ค null และกำหนดค่า default
     */
    private StockForecast createOrUpdateForecast(StockBase stockItem, StockUsageAnalysis usage, int analysisBaseDays) {
        // หา Forecast เดิม (ถ้ามี)
        Optional<StockForecast> existingForecast = stockForecastRepository
                .findTopByStockItemStockItemIdOrderByLastCalculatedDateDesc(stockItem.getStockItemId());

        StockForecast forecast = existingForecast.orElse(new StockForecast());

        // ข้อมูลพื้นฐาน
        forecast.setStockItem(stockItem);
        forecast.setStockItemName(stockItem.getName());
        forecast.setStockType(stockItem.getStockType());
        forecast.setCurrentStock(stockItem.getQuantity() != null ? stockItem.getQuantity() : 0);
        forecast.setCurrentStockValue(stockItem.calculateTotalCost());

        // การวิเคราะห์ความต้องการ
        forecast.setAverageDailyUsage(usage.getAverageDailyUsage());
        forecast.setAverageWeeklyUsage(usage.getAverageWeeklyUsage());
        forecast.setAverageMonthlyUsage(usage.getAverageMonthlyUsage());

        // 🔧 FIX: กำหนดค่า default สำหรับ safetyStockDays และ leadTimeDays ก่อน
        if (forecast.getSafetyStockDays() == null) {
            forecast.setSafetyStockDays(DEFAULT_SAFETY_STOCK_DAYS);
        }
        if (forecast.getLeadTimeDays() == null) {
            forecast.setLeadTimeDays(DEFAULT_LEAD_TIME_DAYS);
        }

        // การคาดการณ์
        calculateForecastPredictions(forecast, usage);

        // คำแนะนำการสั่งซื้อ
        calculateOrderRecommendations(forecast, stockItem);

        // ข้อมูลการวิเคราะห์
        forecast.setAnalysisBasedOnDays(analysisBaseDays);

        // คำนวณระดับความเร่งด่วนและคำแนะนำ
        forecast.calculateUrgencyLevel();
        forecast.generateRecommendations();

        return forecast;
    }

    /**
     * ✅ คำนวณการคาดการณ์ (วันที่จะหมด Stock)
     */
    private void calculateForecastPredictions(StockForecast forecast, StockUsageAnalysis usage) {
        int currentStock = forecast.getCurrentStock();
        int dailyUsage = usage.getAverageDailyUsage();

        if (dailyUsage <= 0) {
            // ไม่มีการใช้งาน หรือใช้งานน้อยมาก
            forecast.setDaysUntilStockOut(999); // กำหนดเป็นจำนวนมากๆ
            forecast.setEstimatedStockOutDate(LocalDateTime.now().plusDays(999));
        } else {
            int daysUntilStockOut = currentStock / dailyUsage;
            forecast.setDaysUntilStockOut(daysUntilStockOut);
            forecast.setEstimatedStockOutDate(LocalDateTime.now().plusDays(daysUntilStockOut));
        }
    }

    /**
     * ✅ คำนวณคำแนะนำการสั่งซื้อ
     * 🔧 FIX: เพิ่มการเช็ค null
     */
    private void calculateOrderRecommendations(StockForecast forecast, StockBase stockItem) {
        int averageMonthlyUsage = forecast.getAverageMonthlyUsage() != null ? forecast.getAverageMonthlyUsage() : 0;
        int averageDailyUsage = forecast.getAverageDailyUsage() != null ? forecast.getAverageDailyUsage() : 0;
        int safetyStockDays = forecast.getSafetyStockDays() != null ? forecast.getSafetyStockDays() : DEFAULT_SAFETY_STOCK_DAYS;
        int leadTimeDays = forecast.getLeadTimeDays() != null ? forecast.getLeadTimeDays() : DEFAULT_LEAD_TIME_DAYS;

        // คำนวณจำนวนที่ควรสั่งซื้อ = การใช้งาน 1 เดือน + Safety Stock + Lead Time Stock
        int safetyStock = (averageDailyUsage * safetyStockDays);
        int leadTimeStock = (averageDailyUsage * leadTimeDays);
        int recommendedOrderQuantity = averageMonthlyUsage + safetyStock + leadTimeStock;

        forecast.setRecommendedOrderQuantity(recommendedOrderQuantity);

        // คำนวณค่าใช้จ่ายโดยประมาณ
        BigDecimal unitCost = stockItem.calculateFinalPrice();
        if (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimatedCost = unitCost.multiply(BigDecimal.valueOf(recommendedOrderQuantity))
                    .setScale(2, RoundingMode.HALF_UP);
            forecast.setEstimatedOrderCost(estimatedCost);
        } else {
            forecast.setEstimatedOrderCost(BigDecimal.ZERO);
        }
    }

    /**
     * ✅ ดึงรายการ Stock ที่ต้องสั่งซื้อเร่งด่วน
     */
    @Transactional(readOnly = true)
    public List<StockForecast> getUrgentStockItems() {
        return stockForecastRepository.findUrgentStockItems();
    }

    /**
     * ✅ ดึงรายการ Stock ที่จะหมดในจำนวนวันที่กำหนด
     */
    @Transactional(readOnly = true)
    public List<StockForecast> getStockRunningOutInDays(int days) {
        return stockForecastRepository.findStockRunningOutInDays(days);
    }

    /**
     * ✅ ดึง Forecast Summary
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getForecastSummary() {
        Map<String, Object> summary = new HashMap<>();

        List<Object[]> urgencyCounts = stockForecastRepository.countByUrgencyLevel();
        Map<String, Long> urgencyMap = new HashMap<>();

        for (Object[] row : urgencyCounts) {
            urgencyMap.put(row[0].toString(), (Long) row[1]);
        }

        summary.put("totalItems", stockForecastRepository.count());
        summary.put("criticalItems", urgencyMap.getOrDefault("CRITICAL", 0L));
        summary.put("highUrgencyItems", urgencyMap.getOrDefault("HIGH", 0L));
        summary.put("mediumUrgencyItems", urgencyMap.getOrDefault("MEDIUM", 0L));
        summary.put("lowUrgencyItems", urgencyMap.getOrDefault("LOW", 0L));

        // คำนวณต้นทุนรวม
        Double criticalCost = stockForecastRepository.getTotalEstimatedCostByUrgencyLevel(StockForecast.UrgencyLevel.CRITICAL);
        Double highCost = stockForecastRepository.getTotalEstimatedCostByUrgencyLevel(StockForecast.UrgencyLevel.HIGH);

        summary.put("criticalItemsCost", criticalCost != null ? criticalCost : 0.0);
        summary.put("highUrgencyItemsCost", highCost != null ? highCost : 0.0);
        summary.put("lastUpdated", LocalDateTime.now());

        return summary;
    }

    /**
     * ✅ ดึง Stock ตาม Type
     */
    @Transactional(readOnly = true)
    public List<StockForecast> getForecastsByStockType(String stockType) {
        return stockForecastRepository.findByStockTypeOrderByUrgencyLevelDescDaysUntilStockOutAsc(stockType);
    }

    /**
     * ✅ ลบ Forecast เก่าที่เกิน 30 วัน
     */
    @Transactional
    public void cleanupOldForecasts() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        stockForecastRepository.deleteOldForecasts(cutoffDate);
        System.out.println("🧹 ลบ Stock Forecast เก่าที่เกิน 30 วันแล้ว");
    }

    /**
     * ✅ Helper Class สำหรับเก็บผลการวิเคราะห์
     */
    private static class StockUsageAnalysis {
        private final Map<LocalDateTime, Integer> dailyUsage;
        private final int totalUsage;
        private final int averageDailyUsage;
        private final int averageWeeklyUsage;
        private final int averageMonthlyUsage;
        private final LocalDateTime analysisStartDate;

        public StockUsageAnalysis() {
            this.dailyUsage = new HashMap<>();
            this.totalUsage = 0;
            this.averageDailyUsage = 0;
            this.averageWeeklyUsage = 0;
            this.averageMonthlyUsage = 0;
            this.analysisStartDate = LocalDateTime.now();
        }

        public StockUsageAnalysis(Map<LocalDateTime, Integer> dailyUsage, int totalUsage, LocalDateTime analysisStartDate) {
            this.dailyUsage = dailyUsage;
            this.totalUsage = totalUsage;
            this.analysisStartDate = analysisStartDate;

            // คำนวณค่าเฉลี่ย
            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(analysisStartDate, LocalDateTime.now());

            if (daysBetween > 0) {
                this.averageDailyUsage = totalUsage / (int) daysBetween;
                this.averageWeeklyUsage = averageDailyUsage * 7;
                this.averageMonthlyUsage = averageDailyUsage * 30;
            } else {
                this.averageDailyUsage = 0;
                this.averageWeeklyUsage = 0;
                this.averageMonthlyUsage = 0;
            }
        }

        // Getters
        public Map<LocalDateTime, Integer> getDailyUsage() { return dailyUsage; }
        public int getTotalUsage() { return totalUsage; }
        public int getAverageDailyUsage() { return averageDailyUsage; }
        public int getAverageWeeklyUsage() { return averageWeeklyUsage; }
        public int getAverageMonthlyUsage() { return averageMonthlyUsage; }
        public LocalDateTime getAnalysisStartDate() { return analysisStartDate; }
    }
}