package com.example.server.mapper;

import com.example.server.dto.OrderDTO;
import com.example.server.dto.OrderItemDTO;
import com.example.server.entity.Order;
import com.example.server.entity.OrderItem;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public OrderDTO toOrderDTO(Order order) {
        if (order == null) return null;

        OrderDTO dto = new OrderDTO();
        dto.setOrderId(order.getOrderId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setSource(order.getSource() != null ? order.getSource().name() : null);
        dto.setCustomerId(order.getCustomer() != null ? order.getCustomer().getCustomerId() : null);
        dto.setCustomerName(order.getCustomerName());
        dto.setCustomerPhone(order.getCustomerPhone());
        dto.setShippingAddress(order.getShippingAddress());
        dto.setOrderDate(order.getOrderDate());
        dto.setDeliveryDate(order.getDeliveryDate());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setShippingFee(order.getShippingFee());
        dto.setDiscount(order.getDiscount());
        dto.setNetAmount(order.getNetAmount());
        dto.setStatus(order.getStatus() != null ? order.getStatus().name() : null);
        dto.setPaymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null);
        dto.setNotes(order.getNotes());
        dto.setTrackingNumber(order.getTrackingNumber());
        dto.setOriginalFileName(order.getOriginalFileName());
        dto.setCreatedDate(order.getCreatedDate());
        dto.setUpdatedDate(order.getUpdatedDate());

        if (order.getOrderItems() != null) {
            dto.setOrderItems(order.getOrderItems().stream()
                    .map(this::toOrderItemDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    public OrderItemDTO toOrderItemDTO(OrderItem item) {
        if (item == null) return null;

        OrderItemDTO dto = new OrderItemDTO();
        dto.setOrderItemId(item.getOrderItemId());
        dto.setOrderId(item.getOrder() != null ? item.getOrder().getOrderId() : null);
        dto.setProductId(item.getProduct() != null ? item.getProduct().getProductId() : null);
        dto.setProductName(item.getProductName());
        dto.setProductSku(item.getProductSku());
        dto.setQuantity(item.getQuantity());
        dto.setUnitPrice(item.getUnitPrice());
        dto.setDiscount(item.getDiscount());
        dto.setTotalPrice(item.getTotalPrice());
        dto.setCostPerUnit(item.getCostPerUnit());
        dto.setTotalCost(item.getTotalCost());
        dto.setProfit(item.getProfit());
        dto.setNotes(item.getNotes());
        dto.setStockDeductionStatus(item.getStockDeductionStatus() != null ?
                item.getStockDeductionStatus().name() : null);

        return dto;
    }

    public List<OrderDTO> toOrderDTOList(List<Order> orders) {
        if (orders == null) return null;
        return orders.stream()
                .map(this::toOrderDTO)
                .collect(Collectors.toList());
    }

    public List<OrderItemDTO> toOrderItemDTOList(List<OrderItem> items) {
        if (items == null) return null;
        return items.stream()
                .map(this::toOrderItemDTO)
                .collect(Collectors.toList());
    }
}