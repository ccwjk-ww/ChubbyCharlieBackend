package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderSummaryDTO {
    private Long totalOrders;
    private Long pendingOrders;
    private Long shippedOrders;
    private Long deliveredOrders;
    private Long cancelledOrders;
    private BigDecimal totalRevenue;
    private BigDecimal totalProfit;
    private BigDecimal averageOrderValue;
    private Long shopeeOrderCount;
    private Long shop24OrderCount;
}
