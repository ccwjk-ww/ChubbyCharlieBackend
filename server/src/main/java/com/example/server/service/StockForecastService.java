package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ✅ Enhanced Stock Forecast Service - FIXED VERSION
 * แก้ไข error: เพิ่ม methods ที่ Controller ต้องการ
 */
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

    private static final int DEFAULT_SAFETY_STOCK_DAYS = 7;
    private static final int DEFAULT_LEAD_TIME_DAYS = 14;
    private static final int ANALYSIS_MONTHS = 6;

    // ============================================
    // ⭐ FIXED: เพิ่ม method ที่ Controller ต้องการ
    // ============================================

    /**
     * ✅ FIXED: method สำหรับ Controller เรียกใช้
     */
    @Transactional
    public StockForecast calculateEnhancedStockForecast(Long stockItemId) {
        // เรียกใช้ method เดิม
        return calculateStockForecast(stockItemId, 180);
    }

    /**
     * ✅ FIXED: ดึง Forecast ทั้งหมด
     */
    @Transactional(readOnly = true)
    public List<StockForecast> getAllForecasts() {
        return stockForecastRepository.findAll();
    }

    // ============================================
    // การคำนวณ Forecast
    // ============================================

    @Transactional
    public List<StockForecast> calculateAllStockForecasts(int analysisBaseDays) {
        System.out.println("📊 เริ่มคำนวณ Stock Forecast สำหรับ Stock Items ทั้งหมด...");

        List<StockBase> allStockItems = stockBaseRepository.findAll();
        List<StockForecast> forecasts = new ArrayList<>();

        int processed = 0;
        int successCount = 0;

        for (StockBase stockItem : allStockItems) {
            try {
                StockForecast forecast = calculateStockForecast(stockItem.getStockItemId(), analysisBaseDays);
                if (forecast != null) {
                    forecasts.add(forecast);
                    successCount++;
                }
                processed++;

                if (processed % 10 == 0) {
                    System.out.printf("📊 ประมวลผลแล้ว %d/%d items\n", processed, allStockItems.size());
                }
            } catch (Exception e) {
                System.err.printf("❌ Error for Stock ID %d: %s\n", stockItem.getStockItemId(), e.getMessage());
            }
        }

        System.out.printf("✅ เสร็จสิ้น: สำเร็จ %d items\n", successCount);
        return forecasts;
    }

    @Transactional
    public StockForecast calculateStockForecast(Long stockItemId, int analysisBaseDays) {
        StockBase stockItem = stockBaseRepository.findById(stockItemId)
                .orElseThrow(() -> new RuntimeException("Stock Item not found: " + stockItemId));

        MonthlyUsageAnalysis monthlyAnalysis = analyzeMonthlyUsage(stockItemId);
        MonthlyForecast nextMonthForecast = predictNextMonthUsage(monthlyAnalysis);

        StockForecast forecast = createEnhancedForecast(stockItem, monthlyAnalysis, nextMonthForecast, analysisBaseDays);
        return stockForecastRepository.save(forecast);
    }

    // ============================================
    // การวิเคราะห์รายเดือน
    // ============================================

    private MonthlyUsageAnalysis analyzeMonthlyUsage(Long stockItemId) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(ANALYSIS_MONTHS - 1);

        List<Product> productsUsingStock = productIngredientRepository.findProductsUsingStockItem(stockItemId);

        if (productsUsingStock.isEmpty()) {
            return new MonthlyUsageAnalysis();
        }

        Map<YearMonth, MonthlyUsageData> monthlyUsageMap = new TreeMap<>();
        for (int i = 0; i < ANALYSIS_MONTHS; i++) {
            YearMonth month = startMonth.plusMonths(i);
            monthlyUsageMap.put(month, new MonthlyUsageData(month));
        }

        for (Product product : productsUsingStock) {
            List<OrderItem> orderItems = orderItemRepository.findByProductProductId(product.getProductId());

            for (OrderItem orderItem : orderItems) {
                Order order = orderItem.getOrder();

                if (order != null && order.getOrderDate() != null &&
                        order.getStatus() != Order.OrderStatus.CANCELLED &&
                        order.getStatus() != Order.OrderStatus.RETURNED) {

                    YearMonth orderMonth = YearMonth.from(order.getOrderDate());

                    if (monthlyUsageMap.containsKey(orderMonth)) {
                        int stockUsed = calculateStockUsageForOrderItem(stockItemId, orderItem);
                        if (stockUsed > 0) {
                            monthlyUsageMap.get(orderMonth).addUsage(stockUsed, orderItem.getQuantity());
                        }
                    }
                }
            }
        }

        return new MonthlyUsageAnalysis(monthlyUsageMap);
    }

    private MonthlyForecast predictNextMonthUsage(MonthlyUsageAnalysis analysis) {
        YearMonth nextMonth = YearMonth.now().plusMonths(1);
        List<Integer> monthlyUsages = analysis.getMonthlyUsages();

        if (monthlyUsages.isEmpty() || monthlyUsages.stream().allMatch(u -> u == 0)) {
            return new MonthlyForecast(nextMonth, 0, "NO_DATA", 0.0);
        }

        double simpleAverage = monthlyUsages.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double weightedAverage = calculateWeightedAverage(monthlyUsages);
        double linearTrend = calculateLinearTrend(monthlyUsages);

        String forecastMethod;
        int predictedUsage;
        double confidence;

        if (Math.abs(linearTrend - simpleAverage) > simpleAverage * 0.2) {
            forecastMethod = "LINEAR_REGRESSION";
            predictedUsage = (int) Math.round(linearTrend);
            confidence = 75.0;
        } else {
            forecastMethod = "WEIGHTED_AVERAGE";
            predictedUsage = (int) Math.round(weightedAverage);
            confidence = 85.0;
        }

        if (predictedUsage < 0) {
            predictedUsage = (int) Math.round(simpleAverage);
            forecastMethod = "SIMPLE_AVERAGE";
            confidence = 60.0;
        }

        return new MonthlyForecast(nextMonth, predictedUsage, forecastMethod, confidence);
    }

    private double calculateWeightedAverage(List<Integer> usages) {
        if (usages.isEmpty()) return 0.0;

        double sum = 0.0;
        double totalWeight = 0.0;
        int size = usages.size();

        for (int i = 0; i < size; i++) {
            int weight = i + 1;
            sum += usages.get(i) * weight;
            totalWeight += weight;
        }

        return sum / totalWeight;
    }

    private double calculateLinearTrend(List<Integer> usages) {
        int n = usages.size();
        if (n < 2) return usages.isEmpty() ? 0.0 : usages.get(0);

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += usages.get(i);
            sumXY += i * usages.get(i);
            sumX2 += i * i;
        }

        double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        return slope * n + intercept;
    }

    private TrendAnalysis analyzeTrend(List<Integer> usages) {
        if (usages.size() < 2) {
            return new TrendAnalysis("STABLE", 0.0, 0);
        }

        int mid = usages.size() / 2;
        double firstHalfAvg = usages.subList(0, mid).stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double secondHalfAvg = usages.subList(mid, usages.size()).stream().mapToInt(Integer::intValue).average().orElse(0.0);

        double changePercent = ((secondHalfAvg - firstHalfAvg) / (firstHalfAvg == 0 ? 1 : firstHalfAvg)) * 100;

        String trend;
        int direction;

        if (Math.abs(changePercent) < 10) {
            trend = "STABLE";
            direction = 0;
        } else if (changePercent > 0) {
            trend = "INCREASING";
            direction = 1;
        } else {
            trend = "DECREASING";
            direction = -1;
        }

        return new TrendAnalysis(trend, changePercent, direction);
    }

    private StockForecast createEnhancedForecast(StockBase stockItem, MonthlyUsageAnalysis monthlyAnalysis,
                                                 MonthlyForecast nextMonthForecast, int analysisBaseDays) {
        Optional<StockForecast> existingForecast = stockForecastRepository
                .findTopByStockItemStockItemIdOrderByLastCalculatedDateDesc(stockItem.getStockItemId());

        StockForecast forecast = existingForecast.orElse(new StockForecast());

        forecast.setStockItem(stockItem);
        forecast.setStockItemName(stockItem.getName());
        forecast.setStockType(stockItem.getStockType());
        forecast.setCurrentStock(stockItem.getQuantity() != null ? stockItem.getQuantity() : 0);
        forecast.setCurrentStockValue(stockItem.calculateTotalCost());

        int averageMonthlyUsage = (int) Math.round(monthlyAnalysis.getAverageMonthlyUsage());
        forecast.setAverageMonthlyUsage(averageMonthlyUsage);
        forecast.setAverageDailyUsage(averageMonthlyUsage / 30);
        forecast.setAverageWeeklyUsage(averageMonthlyUsage / 4);

        int currentStock = forecast.getCurrentStock();
        int dailyUsage = forecast.getAverageDailyUsage();

        if (dailyUsage > 0) {
            int daysUntilStockOut = currentStock / dailyUsage;
            forecast.setDaysUntilStockOut(daysUntilStockOut);
            forecast.setEstimatedStockOutDate(LocalDateTime.now().plusDays(daysUntilStockOut));
        } else {
            forecast.setDaysUntilStockOut(999);
            forecast.setEstimatedStockOutDate(LocalDateTime.now().plusDays(999));
        }

        int predictedNextMonth = nextMonthForecast.getPredictedUsage();
        int safetyStock = forecast.getAverageDailyUsage() * DEFAULT_SAFETY_STOCK_DAYS;
        int leadTimeStock = forecast.getAverageDailyUsage() * DEFAULT_LEAD_TIME_DAYS;

        int recommendedOrder = Math.max(0, predictedNextMonth + safetyStock + leadTimeStock - currentStock);
        forecast.setRecommendedOrderQuantity(recommendedOrder);

        BigDecimal unitCost = stockItem.calculateFinalPrice();
        if (unitCost != null && unitCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal estimatedCost = unitCost.multiply(BigDecimal.valueOf(recommendedOrder))
                    .setScale(2, RoundingMode.HALF_UP);
            forecast.setEstimatedOrderCost(estimatedCost);
        } else {
            forecast.setEstimatedOrderCost(BigDecimal.ZERO);
        }

        if (forecast.getSafetyStockDays() == null) {
            forecast.setSafetyStockDays(DEFAULT_SAFETY_STOCK_DAYS);
        }
        if (forecast.getLeadTimeDays() == null) {
            forecast.setLeadTimeDays(DEFAULT_LEAD_TIME_DAYS);
        }

        forecast.setAnalysisBasedOnDays(analysisBaseDays);
        forecast.calculateUrgencyLevel();
        forecast.generateRecommendations();

        TrendAnalysis trend = analyzeTrend(monthlyAnalysis.getMonthlyUsages());
        String enhancedRec = String.format(
                "\n\n📈 การวิเคราะห์รายเดือน (6 เดือนย้อนหลัง):\n" +
                        "   • แนวโน้ม: %s (%.1f%%)\n" +
                        "   • คาดการณ์เดือน %s: %d ชิ้น\n" +
                        "   • วิธีคาดการณ์: %s (ความมั่นใจ %.1f%%)\n" +
                        "   • แนะนำสั่งซื้อ: %d ชิ้น (%.2f บาท)",
                trend.getTrend(), trend.getChangePercent(),
                nextMonthForecast.getNextMonth(), nextMonthForecast.getPredictedUsage(),
                nextMonthForecast.getForecastMethod(), nextMonthForecast.getConfidence(),
                recommendedOrder, forecast.getEstimatedOrderCost()
        );

        forecast.setRecommendations(forecast.getRecommendations() + enhancedRec);

        return forecast;
    }

    private int calculateStockUsageForOrderItem(Long stockItemId, OrderItem orderItem) {
        List<ProductIngredient> ingredients = productIngredientRepository.findByStockItemStockItemId(stockItemId);

        for (ProductIngredient ingredient : ingredients) {
            if (ingredient.getProduct().getProductId().equals(orderItem.getProduct().getProductId())) {
                BigDecimal requiredQuantity = ingredient.getRequiredQuantity();
                Integer orderQuantity = orderItem.getQuantity();

                if (requiredQuantity != null && orderQuantity != null) {
                    return (int) (orderQuantity * requiredQuantity.doubleValue());
                }
            }
        }
        return 0;
    }

    // ============================================
    // Query Methods
    // ============================================

    @Transactional(readOnly = true)
    public List<StockForecast> getUrgentStockItems() {
        return stockForecastRepository.findUrgentStockItems();
    }

    @Transactional(readOnly = true)
    public List<StockForecast> getStockRunningOutInDays(int days) {
        return stockForecastRepository.findStockRunningOutInDays(days);
    }

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

        Double criticalCost = stockForecastRepository.getTotalEstimatedCostByUrgencyLevel(StockForecast.UrgencyLevel.CRITICAL);
        Double highCost = stockForecastRepository.getTotalEstimatedCostByUrgencyLevel(StockForecast.UrgencyLevel.HIGH);

        summary.put("criticalItemsCost", criticalCost != null ? criticalCost : 0.0);
        summary.put("highUrgencyItemsCost", highCost != null ? highCost : 0.0);
        summary.put("lastUpdated", LocalDateTime.now());

        return summary;
    }

    @Transactional(readOnly = true)
    public List<StockForecast> getForecastsByStockType(String stockType) {
        return stockForecastRepository.findByStockTypeOrderByUrgencyLevelDescDaysUntilStockOutAsc(stockType);
    }

    @Transactional
    public void cleanupOldForecasts() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        stockForecastRepository.deleteOldForecasts(cutoffDate);
    }

    // ============================================
    // Helper Classes
    // ============================================

    private static class MonthlyUsageData {
        private final YearMonth month;
        private int totalUsage = 0;
        private int totalOrders = 0;

        public MonthlyUsageData(YearMonth month) {
            this.month = month;
        }

        public void addUsage(int stockUsed, int orderQuantity) {
            this.totalUsage += stockUsed;
            this.totalOrders += orderQuantity;
        }

        public int getTotalUsage() {
            return totalUsage;
        }
    }

    private static class MonthlyUsageAnalysis {
        private final Map<YearMonth, MonthlyUsageData> monthlyData;
        private final List<Integer> monthlyUsages;
        private final double averageMonthlyUsage;

        public MonthlyUsageAnalysis() {
            this.monthlyData = new TreeMap<>();
            this.monthlyUsages = new ArrayList<>();
            this.averageMonthlyUsage = 0.0;
        }

        public MonthlyUsageAnalysis(Map<YearMonth, MonthlyUsageData> monthlyData) {
            this.monthlyData = monthlyData;
            this.monthlyUsages = monthlyData.values().stream()
                    .map(MonthlyUsageData::getTotalUsage)
                    .collect(Collectors.toList());
            this.averageMonthlyUsage = monthlyUsages.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0);
        }

        public List<Integer> getMonthlyUsages() {
            return monthlyUsages;
        }

        public double getAverageMonthlyUsage() {
            return averageMonthlyUsage;
        }
    }

    private static class MonthlyForecast {
        private final YearMonth nextMonth;
        private final int predictedUsage;
        private final String forecastMethod;
        private final double confidence;

        public MonthlyForecast(YearMonth nextMonth, int predictedUsage, String forecastMethod, double confidence) {
            this.nextMonth = nextMonth;
            this.predictedUsage = predictedUsage;
            this.forecastMethod = forecastMethod;
            this.confidence = confidence;
        }

        public YearMonth getNextMonth() {
            return nextMonth;
        }

        public int getPredictedUsage() {
            return predictedUsage;
        }

        public String getForecastMethod() {
            return forecastMethod;
        }

        public double getConfidence() {
            return confidence;
        }
    }

    private static class TrendAnalysis {
        private final String trend;
        private final double changePercent;
        private final int direction;

        public TrendAnalysis(String trend, double changePercent, int direction) {
            this.trend = trend;
            this.changePercent = changePercent;
            this.direction = direction;
        }

        public String getTrend() {
            return trend;
        }

        public double getChangePercent() {
            return changePercent;
        }

        public int getDirection() {
            return direction;
        }
    }
}