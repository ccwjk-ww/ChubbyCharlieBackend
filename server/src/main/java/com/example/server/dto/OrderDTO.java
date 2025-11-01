package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderDTO {
    private Long orderId;
    private String orderNumber;
    private String source;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private BigDecimal totalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discount;
    private BigDecimal netAmount;
    private String status;
    private String paymentStatus;
    private String notes;
    private String trackingNumber;
    private String originalFileName;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private List<OrderItemDTO> orderItems;
}

