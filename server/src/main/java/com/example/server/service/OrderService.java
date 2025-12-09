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

    // ⭐ NEW: เพิ่ม TransactionService
    @Autowired
    private TransactionService transactionService;

    // ============================================
    // CRUD Operations - FIXED
    // ============================================

    /**
     * ✅ แก้ไข: ใช้ JOIN FETCH เพื่อหลีกเลี่ยง N+1 query
     * และคำนวณ totals ทุก order
     */
    @Transactional(readOnly = false) // ⭐ เปลี่ยนเป็น false เพื่อ save ได้
    public List<Order> getAllOrders() {
        // ✅ ใช้ JOIN FETCH แทน lazy loading
        List<Order> orders = orderRepository.findAllOrdersWithItems();

        // ✅ คำนวณและบันทึก totals สำหรับทุก order
        for (Order order : orders) {
            order.calculateTotals();
        }

        // ✅ Batch save เพื่อประสิทธิภาพ
        orderRepository.saveAll(orders);

        return orders;
    }

    /**
     * ✅ แก้ไข: ใช้ JOIN FETCH และ save totals
     */
    @Transactional(readOnly = false) // ⭐ เปลี่ยนเป็น false
    public Optional<Order> getOrderById(Long id) {
        // ✅ ใช้ query ที่ fetch items พร้อมกัน
        Optional<Order> orderOpt = orderRepository.findByIdWithItems(id);

        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();

            // ✅ คำนวณ totals
            order.calculateTotals();

            // ✅ บันทึกการเปลี่ยนแปลง
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
    // Create Order - FIXED ORPHAN REMOVAL
    // ============================================

    /**
     * ✅ ปรับปรุง: ลดจำนวน database calls และป้องกัน orphan removal error
     */
    public Order createOrder(Order order, List<OrderItem> items) {
        validateOrder(order);

        // Generate order number
        if (order.getOrderNumber() == null || order.getOrderNumber().trim().isEmpty()) {
            order.setOrderNumber(generateOrderNumber(order.getSource()));
        }

        // Check duplicate
        if (orderRepository.findByOrderNumber(order.getOrderNumber()).isPresent()) {
            throw new IllegalArgumentException("Order number already exists: " + order.getOrderNumber());
        }

        // ✅ Initialize default values
        if (order.getShippingFee() == null) order.setShippingFee(BigDecimal.ZERO);
        if (order.getDiscount() == null) order.setDiscount(BigDecimal.ZERO);
        if (order.getStatus() == null) order.setStatus(Order.OrderStatus.PENDING);
        if (order.getPaymentStatus() == null) order.setPaymentStatus(Order.PaymentStatus.UNPAID);

        // Save order first
        Order savedOrder = orderRepository.save(order);

        if (items != null && !items.isEmpty()) {
            // ✅ Process all items
            for (OrderItem item : items) {
                item.setOrder(savedOrder);

                // Initialize defaults
                if (item.getDiscount() == null) item.setDiscount(BigDecimal.ZERO);
                if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
                if (item.getQuantity() == null) item.setQuantity(1);
                if (item.getStockDeductionStatus() == null) {
                    item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
                }

                // Load product if needed
                if (item.getProduct() != null && item.getProduct().getProductId() != null) {
                    loadProductInfo(item, item.getProduct().getProductId());
                } else if (item.getProductSku() != null && !item.getProductSku().trim().isEmpty()) {
                    loadProductInfoBySku(item, item.getProductSku());
                }

                // ✅ คำนวณ totals ก่อนเพิ่มเข้า collection
                item.calculateTotals();

                // ⭐ เพิ่มเข้า collection เดิม
                savedOrder.getOrderItems().add(item);
            }

            // ✅ คำนวณยอดรวมของ order
            savedOrder.calculateTotals();

            // Save order (cascade จะ save items ด้วย)
            savedOrder = orderRepository.save(savedOrder);
        }

        return savedOrder;
    }

    // ============================================
    // Update Order - FIXED ORPHAN REMOVAL ERROR
    // ============================================

    /**
     * ✅ แก้ไข: จัดการ items อย่างถูกต้องกับ Orphan Removal
     * @param id Order ID
     * @param orderDetails ข้อมูล order ที่จะอัพเดท
     * @param newItems รายการสินค้าใหม่ (ถ้ามี)
     */
    @Transactional
    public Order updateOrder(Long id, Order orderDetails, List<OrderItem> newItems) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

        // Update order fields
        updateOrderFields(order, orderDetails);
        validateOrder(order);

        // ✅ จัดการ items ถ้ามีการส่งมา
        if (newItems != null && !newItems.isEmpty()) {
            // ⭐ วิธีที่ถูกต้อง: Modify collection เดิม ไม่ใช่สร้างใหม่

            // 1. Clear items เดิมทั้งหมด (orphan removal จะลบจาก DB)
            order.getOrderItems().clear();

            // 2. Flush เพื่อให้แน่ใจว่าลบจาก DB แล้ว
            orderItemRepository.flush();

            // 3. เตรียม items ใหม่
            for (OrderItem item : newItems) {
                item.setOrder(order); // Set reference กลับไปหา order

                // Initialize defaults
                if (item.getDiscount() == null) item.setDiscount(BigDecimal.ZERO);
                if (item.getUnitPrice() == null) item.setUnitPrice(BigDecimal.ZERO);
                if (item.getQuantity() == null) item.setQuantity(1);

                // Load product info
                if (item.getProduct() != null && item.getProduct().getProductId() != null) {
                    loadProductInfo(item, item.getProduct().getProductId());
                } else if (item.getProductSku() != null && !item.getProductSku().trim().isEmpty()) {
                    loadProductInfoBySku(item, item.getProductSku());
                }

                // คำนวณ totals
                item.calculateTotals();

                // ⭐ เพิ่มเข้า collection เดิม (ไม่ใช่ setOrderItems)
                order.getOrderItems().add(item);
            }

        } else {
            // ✅ ถ้าไม่ได้ส่ง items มา ให้คำนวณ items ที่มีอยู่
            if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
                for (OrderItem item : order.getOrderItems()) {
                    item.calculateTotals();
                }
            }
        }

        // ✅ คำนวณยอดรวมของ order
        order.calculateTotals();

        // Save order (cascade จะ save items ด้วย)
        return orderRepository.save(order);
    }

    /**
     * ⭐ Overload method สำหรับ backward compatibility
     */
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

    /**
     * ⭐ แก้ไข: อัพเดท Payment Status และสร้าง Transaction เมื่อชำระเงิน
     */
    public Order updatePaymentStatus(Long id, Order.PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Order.PaymentStatus oldStatus = order.getPaymentStatus();
        order.setPaymentStatus(paymentStatus);
        order.setUpdatedDate(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // ⭐ ถ้าเปลี่ยนจาก UNPAID -> PAID ให้สร้าง Transaction อัตโนมัติ
        if (oldStatus != Order.PaymentStatus.PAID &&
                paymentStatus == Order.PaymentStatus.PAID) {
            try {
                transactionService.createOrderPaymentTransaction(savedOrder);
            } catch (Exception e) {
                System.err.println("Failed to create transaction for order " + savedOrder.getOrderNumber() + ": " + e.getMessage());
            }
        }

        return savedOrder;
    }

    // ============================================
    // Delete & Cancel
    // ============================================

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new RuntimeException("Order not found");
        }

        // ✅ Cascade delete จะลบ items อัตโนมัติถ้าตั้งค่าถูกต้อง
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

        // Update item statuses
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

        // Load product info
        if (item.getProduct() != null && item.getProduct().getProductId() != null) {
            loadProductInfo(item, item.getProduct().getProductId());
        } else if (item.getProductSku() != null) {
            loadProductInfoBySku(item, item.getProductSku());
        }

        // Calculate item totals
        item.calculateTotals();
        OrderItem savedItem = orderItemRepository.save(item);

        // Update order totals
        order.calculateTotals();
        orderRepository.save(order);

        return savedItem;
    }

    public void removeOrderItem(Long itemId) {
        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Order item not found"));

        Order order = item.getOrder();
        orderItemRepository.deleteById(itemId);

        // Reload order with remaining items
        order = orderRepository.findByIdWithItems(order.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.calculateTotals();
        orderRepository.save(order);
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * ✅ แยก logic การโหลด product ออกมา
     */
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

    /**
     * ✅ โหลด product ด้วย SKU
     */
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

        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));

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

        order.setUpdatedDate(LocalDateTime.now());
    }
}