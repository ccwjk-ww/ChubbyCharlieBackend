package com.example.server.mapper;

import com.example.server.dto.StockForecastDTO;
import com.example.server.dto.StockForecastSummaryDTO;
import com.example.server.dto.StockOrderRecommendationDTO;
import com.example.server.entity.StockForecast;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StockForecastMapper {

    /**
     * ✅ แปลง StockForecast Entity เป็น DTO
     */
    public StockForecastDTO toStockForecastDTO(StockForecast forecast) {
        if (forecast == null) return null;

        StockForecastDTO dto = new StockForecastDTO();
        dto.setForecastId(forecast.getForecastId());
        dto.setStockItemId(forecast.getStockItem() != null ? forecast.getStockItem().getStockItemId() : null);
        dto.setStockItemName(forecast.getStockItemName());
        dto.setStockType(forecast.getStockType());

        // ข้อมูล Stock ปัจจุบัน
        dto.setCurrentStock(forecast.getCurrentStock());
        dto.setCurrentStockValue(forecast.getCurrentStockValue());

        // การวิเคราะห์ความต้องการ
        dto.setAverageDailyUsage(forecast.getAverageDailyUsage());
        dto.setAverageWeeklyUsage(forecast.getAverageWeeklyUsage());
        dto.setAverageMonthlyUsage(forecast.getAverageMonthlyUsage());

        // การคาดการณ์
        dto.setDaysUntilStockOut(forecast.getDaysUntilStockOut());
        dto.setEstimatedStockOutDate(forecast.getEstimatedStockOutDate());

        // คำแนะนำการสั่งซื้อ
        dto.setRecommendedOrderQuantity(forecast.getRecommendedOrderQuantity());
        dto.setEstimatedOrderCost(forecast.getEstimatedOrderCost());

        // สถานะความเร่งด่วน
        dto.setUrgencyLevel(forecast.getUrgencyLevel() != null ? forecast.getUrgencyLevel().name() : null);
        dto.setUrgencyDescription(forecast.getUrgencyLevel() != null ? forecast.getUrgencyLevel().getDescription() : null);
        dto.setRecommendations(forecast.getRecommendations());

        // ข้อมูลการวิเคราะห์
        dto.setAnalysisBasedOnDays(forecast.getAnalysisBasedOnDays());
        dto.setLastCalculatedDate(forecast.getLastCalculatedDate());
        dto.setSafetyStockDays(forecast.getSafetyStockDays());
        dto.setLeadTimeDays(forecast.getLeadTimeDays());

        return dto;
    }

    /**
     * ✅ แปลง List ของ StockForecast เป็น List ของ DTO
     */
    public List<StockForecastDTO> toStockForecastDTOList(List<StockForecast> forecasts) {
        if (forecasts == null) return null;
        return forecasts.stream()
                .map(this::toStockForecastDTO)
                .collect(Collectors.toList());
    }

    /**
     * ✅ สร้าง Summary DTO จาก Map ข้อมูล
     */
    public StockForecastSummaryDTO toStockForecastSummaryDTO(Map<String, Object> summaryMap) {
        if (summaryMap == null) return null;

        StockForecastSummaryDTO dto = new StockForecastSummaryDTO();
        dto.setTotalItems(getLongValue(summaryMap, "totalItems"));
        dto.setCriticalItems(getLongValue(summaryMap, "criticalItems"));
        dto.setHighUrgencyItems(getLongValue(summaryMap, "highUrgencyItems"));
        dto.setMediumUrgencyItems(getLongValue(summaryMap, "mediumUrgencyItems"));
        dto.setLowUrgencyItems(getLongValue(summaryMap, "lowUrgencyItems"));

        dto.setCriticalItemsCost(getBigDecimalValue(summaryMap, "criticalItemsCost"));
        dto.setHighUrgencyItemsCost(getBigDecimalValue(summaryMap, "highUrgencyItemsCost"));

        // คำนวณต้นทุนรวม
        BigDecimal totalCost = BigDecimal.ZERO;
        if (dto.getCriticalItemsCost() != null) {
            totalCost = totalCost.add(dto.getCriticalItemsCost());
        }
        if (dto.getHighUrgencyItemsCost() != null) {
            totalCost = totalCost.add(dto.getHighUrgencyItemsCost());
        }
        dto.setTotalEstimatedCost(totalCost);

        dto.setLastUpdated((java.time.LocalDateTime) summaryMap.get("lastUpdated"));

        return dto;
    }

    /**
     * ✅ สร้าง Order Recommendation DTO
     */
    public StockOrderRecommendationDTO toStockOrderRecommendationDTO(
            List<StockForecast> urgentItems,
            List<StockForecast> soonToOrderItems) {

        StockOrderRecommendationDTO dto = new StockOrderRecommendationDTO();

        // แปลงรายการ
        dto.setUrgentItems(toStockForecastDTOList(urgentItems));
        dto.setSoonToOrderItems(toStockForecastDTOList(soonToOrderItems));

        // รวมรายการทั้งหมด
        List<StockForecast> allItems = new java.util.ArrayList<>();
        allItems.addAll(urgentItems);
        allItems.addAll(soonToOrderItems);

        // คำนวณสรุป
        dto.setTotalItemsToOrder(allItems.size());

        BigDecimal totalCost = allItems.stream()
                .map(StockForecast::getEstimatedOrderCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTotalOrderCost(totalCost);

        // กำหนด Priority Level
        long criticalCount = urgentItems.stream()
                .filter(item -> item.getUrgencyLevel() == StockForecast.UrgencyLevel.CRITICAL)
                .count();

        if (criticalCount > 0) {
            dto.setPriorityLevel("CRITICAL");
        } else if (!urgentItems.isEmpty()) {
            dto.setPriorityLevel("HIGH");
        } else {
            dto.setPriorityLevel("MEDIUM");
        }

        // จัดกลุ่มตาม Stock Type
        dto.setChinaStockOrders(createOrderGroupDTO("CHINA", allItems));
        dto.setThaiStockOrders(createOrderGroupDTO("THAI", allItems));

        return dto;
    }

    /**
     * ✅ สร้าง Order Group DTO ตาม Stock Type
     */
    private StockOrderRecommendationDTO.OrderGroupDTO createOrderGroupDTO(String stockType, List<StockForecast> allItems) {
        List<StockForecast> typeItems = allItems.stream()
                .filter(item -> stockType.equals(item.getStockType()))
                .collect(Collectors.toList());

        StockOrderRecommendationDTO.OrderGroupDTO groupDTO = new StockOrderRecommendationDTO.OrderGroupDTO();
        groupDTO.setStockType(stockType);
        groupDTO.setItemCount(typeItems.size());

        BigDecimal typeTotalCost = typeItems.stream()
                .map(StockForecast::getEstimatedOrderCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        groupDTO.setTotalCost(typeTotalCost);

        groupDTO.setItems(toStockForecastDTOList(typeItems));

        return groupDTO;
    }

    /**
     * Helper methods สำหรับดึงข้อมูลจาก Map
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        return BigDecimal.ZERO;
    }
}