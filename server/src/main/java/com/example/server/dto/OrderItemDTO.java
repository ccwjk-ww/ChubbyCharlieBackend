package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
@Data
public class OrderItemDTO {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productSku;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal discount;
    private BigDecimal totalPrice;
    private BigDecimal costPerUnit;
    private BigDecimal totalCost;
    private BigDecimal profit;
    private String notes;
    private String stockDeductionStatus;
}