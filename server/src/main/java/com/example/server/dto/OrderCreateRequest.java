package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderCreateRequest {
    private String orderNumber;
    private String source;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private String notes;
    private List<OrderItemRequest> orderItems;
}