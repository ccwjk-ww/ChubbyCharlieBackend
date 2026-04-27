package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TransactionService transactionService;

    // ============================================
    // CRUD Operations
    // ============================================

    @Transactional(readOnly = false)
    public List<Order> getAllOrders() {
        List<Order> orders = orderRepository.findAllOrdersWithItems();
        for (Order order : orders) {
            order.calculateTotals();
        }
        orderRepository.saveAll(orders);
        return orders;
    }

    @Transactional(readOnly = false)
    public Optional<Order> getOrderById(Long id) {
        Optional<Order> orderOpt = orderRepository.findByIdWithItems(id);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.calculateTotals();
            orderRepository.save(order);
        }
        return orderOpt;
    }

    @Transactional(readOnly = true)
    public Optional<Order> getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumberWithItems(orderNumber);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(Order.OrderStatus status) {
        return orderRepository.findByStatusWithItems(status);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersBySource(Order.OrderSource source) {
        return orderRepository.findBySourceWithItems(source);
    }

    @Transactional(readOnly = true)
    public List<Order> searchOrders(String keyword) {
        return orderRepository.searchOrdersWithItems(keyword);
    }

    // ============================================
    // Create Order
    // ============================================

    public Order createOrder(Order order, List<OrderItem> items) {
        validateOrder(order);

        if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
            order.setOrderNumber(generateOrderNumber(order.getSource()));
        }

        if (orderRepository.findByOrderNumber(order.getOrderNumber()).isPresent()) {
            throw new IllegalArgumentException("Order number already exists: " + order.getOrderNumber());
        }

        if (order.getShippingFee() == null) order.setShippingFee(BigDecimal.ZERO);
        if (order.getDiscount() == null) order.setDiscount(BigDecimal.ZERO);
        if (order.getStatus() == null) order.setStatus(Order.OrderStatus.PENDING);
        if (order.getPaymentStatus() == null) order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        // ⭐ VAT defaults
        if (order.getVatEnabled() == null) order.setVatEnabled(false);
        if (order.getVatAmount() == null) order.setVatAmount(BigDecimal.ZERO);

        Order savedOrder = orderRepository.save(order);

        if (items != null && !items.isEmpty()) {
            for (OrderItem item : items) {
                item.setOrder(savedOrder);

                if (item.getDiscount() == null) item.setDiscount(BigDecimal.ZERO);
                if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
                if (item.getQuantity() == null) item.setQuantity(1);
                if (item.getStockDeductionStatus() == null) {
                    item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
                }

                if (item.getProduct() != null && item.getProduct().getProductId() != null) {
                    loadProductInfo(item, item.getProduct().getProductId());
                } else if (item.getProductSku() != null && !item.getProductSku().trim().isEmpty()) {
                    loadProductInfoBySku(item, item.getProductSku());
                }

                item.calculateTotals();
                savedOrder.getOrderItems().add(item);
            }

            savedOrder.calculateTotals();
            savedOrder = orderRepository.save(savedOrder);
        }

        return savedOrder;
    }

    // ============================================
    // Update Order
    // ============================================

    @Transactional
    public Order updateOrder(Long id, Order orderDetails, List<OrderItem> newItems) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        updateOrderFields(order, orderDetails);
        validateOrder(order);

        if (newItems != null && !newItems.isEmpty()) {
            order.getOrderItems().clear();
            orderItemRepository.flush();

            for (OrderItem item : newItems) {
                item.setOrder(order);

                if (item.getDiscount() == null) item.setDiscount(BigDecimal.ZERO);
                if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
                if (item.getQuantity() == null) item.setQuantity(1);

                if (item.getProduct() != null && item.getProduct().getProductId() != null) {
                    loadProductInfo(item, item.getProduct().getProductId());
                } else if (item.getProductSku() != null && !item.getProductSku().trim().isEmpty()) {
                    loadProductInfoBySku(item, item.getProductSku());
                }

                item.calculateTotals();
                order.getOrderItems().add(item);
            }
        } else {
            if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
                for (OrderItem item : order.getOrderItems()) {
                    item.calculateTotals();
                }
            }
        }

        order.calculateTotals();
        return orderRepository.save(order);
    }

    @Transactional
    public Order updateOrder(Long id, Order orderDetails) {
        return updateOrder(id, orderDetails, null);
    }

    // ============================================
    // Status Updates
    // ============================================

    public Order updateOrderStatus(Long id, Order.OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(status);
        order.setUpdatedDate(LocalDateTime.now());

        return orderRepository.save(order);
    }

    public Order updatePaymentStatus(Long id, Order.PaymentStatus paymentStatus, LocalDateTime paymentDate) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Order.PaymentStatus oldStatus = order.getPaymentStatus();
        order.setPaymentStatus(paymentStatus);

        if (paymentStatus == Order.PaymentStatus.PAID && paymentDate != null) {
            order.setPaymentDate(paymentDate);
        } else if (paymentStatus == Order.PaymentStatus.PAID && paymentDate == null) {
            order.setPaymentDate(LocalDateTime.now());
        }

        order.setUpdatedDate(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);

        if (oldStatus != Order.PaymentStatus.PAID && paymentStatus == Order.PaymentStatus.PAID) {
            try {
                transactionService.createOrderPaymentTransaction(savedOrder, savedOrder.getPaymentDate());
            } catch (Exception e) {
                System.err.println("Failed to create transaction for order " +
                        savedOrder.getOrderNumber() + ": " + e.getMessage());
            }
        }

        return savedOrder;
    }

    public Order updatePaymentStatus(Long id, Order.PaymentStatus paymentStatus) {
        return updatePaymentStatus(id, paymentStatus, null);
    }

    // ============================================
    // Delete & Cancel
    // ============================================

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found");
        }
        orderRepository.deleteById(id);
    }

    public Order cancelOrder(Long id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == Order.OrderStatus.DELIVERED ||
                order.getStatus() == Order.OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel order with status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setUpdatedDate(LocalDateTime.now());

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                item.setStockDeductionStatus(OrderItem.StockDeductionStatus.CANCELLED);
            }
            orderItemRepository.saveAll(order.getOrderItems());
        }

        return orderRepository.save(order);
    }

    // ============================================
    // Order Item Management
    // ============================================

    public OrderItem addOrderItem(Long orderId, OrderItem item) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        item.setOrder(order);

        if (item.getDiscount() == null) item.setDiscount(BigDecimal.ZERO);

        if (item.getProduct() != null && item.getProduct().getProductId() != null) {
            loadProductInfo(item, item.getProduct().getProductId());
        } else if (item.getProductSku() != null) {
            loadProductInfoBySku(item, item.getProductSku());
        }

        item.calculateTotals();
        OrderItem savedItem = orderItemRepository.save(item);

        order.calculateTotals();
        orderRepository.save(order);

        return savedItem;
    }

    public void removeOrderItem(Long itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        Order order = item.getOrder();
        orderItemRepository.deleteById(itemId);

        order = orderRepository.findByIdWithItems(order.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.calculateTotals();
        orderRepository.save(order);
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void loadProductInfo(OrderItem item, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        item.setProduct(product);
        item.setProductName(product.getProductName());
        item.setProductSku(product.getSku());

        if (product.getCalculatedCost() != null) {
            item.setCostPerUnit(product.getCalculatedCost());
        }
    }

    private void loadProductInfoBySku(OrderItem item, String sku) {
        Optional<Product> productOpt = productRepository.findBySku(sku);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            item.setProduct(product);
            item.setProductName(product.getProductName());
            item.setProductSku(product.getSku());

            if (product.getCalculatedCost() != null) {
                item.setCostPerUnit(product.getCalculatedCost());
            }
        }
    }

    private String generateOrderNumber(Order.OrderSource source) {
        String prefix = switch (source) {
            case SHOP_24 -> "24S";
            case SHOPEE -> "SHP";
            case TIKTOK -> "TIK";
            case MANUAL -> "MAN";
        };
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + "-" + timestamp;
    }

    private void validateOrder(Order order) {
        if (order.getSource() == null) {
            throw new IllegalArgumentException("Order source is required");
        }
        if (order.getCustomerName() == null || order.getCustomerName().trim().isEmpty()) {
            order.setCustomerName("ลูกค้า - " + order.getOrderNumber());
        }
    }

    private void updateOrderFields(Order order, Order details) {
        if (details.getCustomerName() != null)
            order.setCustomerName(details.getCustomerName());
        if (details.getCustomerPhone() != null)
            order.setCustomerPhone(details.getCustomerPhone());
        if (details.getShippingAddress() != null)
            order.setShippingAddress(details.getShippingAddress());
        if (details.getOrderDate() != null)
            order.setOrderDate(details.getOrderDate());
        if (details.getDeliveryDate() != null)
            order.setDeliveryDate(details.getDeliveryDate());
        if (details.getShippingFee() != null)
            order.setShippingFee(details.getShippingFee());
        if (details.getDiscount() != null)
            order.setDiscount(details.getDiscount());
        if (details.getStatus() != null)
            order.setStatus(details.getStatus());
        if (details.getPaymentStatus() != null)
            order.setPaymentStatus(details.getPaymentStatus());
        if (details.getNotes() != null)
            order.setNotes(details.getNotes());
        if (details.getTrackingNumber() != null)
            order.setTrackingNumber(details.getTrackingNumber());

        // ⭐ VAT fields
        if (details.getVatEnabled() != null)
            order.setVatEnabled(details.getVatEnabled());
        if (details.getVatRate() != null)
            order.setVatRate(details.getVatRate());

        order.setUpdatedDate(LocalDateTime.now());
    }
}