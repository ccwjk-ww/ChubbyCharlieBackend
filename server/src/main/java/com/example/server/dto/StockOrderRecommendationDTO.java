package com.example.server.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class StockOrderRecommendationDTO {
    // รายการ Stock ที่ต้องสั่งซื้อ
    private List<StockForecastDTO> urgentItems;
    private List<StockForecastDTO> soonToOrderItems;

    // สรุปการสั่งซื้อ
    private BigDecimal totalOrderCost;
    private Integer totalItemsToOrder;
    private String priorityLevel;

    // กลุ่มตาม Stock Type
    private OrderGroupDTO chinaStockOrders;
    private OrderGroupDTO thaiStockOrders;

    @Data
    public static class OrderGroupDTO {
        private String stockType;
        private Integer itemCount;
        private BigDecimal totalCost;
        private List<StockForecastDTO> items;
    }
}
