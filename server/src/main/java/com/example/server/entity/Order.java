//package com.example.server.entity;
//
//import jakarta.persistence.*;
//import lombok.Data;
//import lombok.EqualsAndHashCode;
//import lombok.ToString;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Entity
//@Data
//@Table(name = "orders")
//@EqualsAndHashCode(exclude = {"orderItems"})
//@ToString(exclude = {"orderItems"})
//public class Order {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
//    private Long orderId;
//
//    @Column(nullable = false, unique = true)
//    private String orderNumber;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private OrderSource source;
//
//    @ManyToOne(fetch = FetchType.LAZY)
//    @JoinColumn(name = "customer_id")
//    private Customer customer;
//
//    private LocalDateTime orderDate;
//    private LocalDateTime deliveryDate;
//
//    @Column(precision = 10, scale = 2)
//    private BigDecimal totalAmount;
//
//    @Column(precision = 10, scale = 2)
//    private BigDecimal shippingFee;
//
//    @Column(precision = 10, scale = 2)
//    private BigDecimal discount;
//
//    @Column(precision = 10, scale = 2)
//    private BigDecimal netAmount;
//
//    @Enumerated(EnumType.STRING)
//    private OrderStatus status = OrderStatus.PENDING;
//
//    @Enumerated(EnumType.STRING)
//    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;
//
//    private String shippingAddress;
//    private String customerPhone;
//    private String customerName;
//    private String notes;
//    private String trackingNumber;
//    private String originalFileName;
//    private String uploadedFilePath;
//
//    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
//    private List<OrderItem> orderItems = new ArrayList<>();
//
//    private LocalDateTime createdDate;
//    private LocalDateTime updatedDate;
//
//    @PrePersist
//    public void onCreate() {
//        createdDate = LocalDateTime.now();
//        updatedDate = LocalDateTime.now();
//    }
//
//    @PreUpdate
//    public void onUpdate() {
//        updatedDate = LocalDateTime.now();
//    }
//
//    public void calculateTotals() {
//        if (orderItems != null && !orderItems.isEmpty()) {
//            this.totalAmount = orderItems.stream()
//                    .map(OrderItem::getTotalPrice)
//                    .filter(price -> price != null)
//                    .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//            BigDecimal shippingAmount = this.shippingFee != null ? this.shippingFee : BigDecimal.ZERO;
//            BigDecimal discountAmount = this.discount != null ? this.discount : BigDecimal.ZERO;
//            this.netAmount = this.totalAmount.add(shippingAmount).subtract(discountAmount);
//        }
//    }
//
//    public enum OrderSource {
//        SHOP_24("24shop"),
//        SHOPEE("Shopee"),
//        MANUAL("Manual");
//
//        private final String displayName;
//
//        OrderSource(String displayName) {
//            this.displayName = displayName;
//        }
//
//        public String getDisplayName() {
//            return displayName;
//        }
//    }
//
//    public enum OrderStatus {
//        PENDING,
//        CONFIRMED,
//        PROCESSING,
//        PACKED,
//        SHIPPED,
//        DELIVERED,
//        CANCELLED,
//        RETURNED
//    }
//
//    public enum PaymentStatus {
//        UNPAID,
//        PAID,
//        REFUNDED
//    }
//}

package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "orders")
@EqualsAndHashCode(exclude = {"orderItems"})
@ToString(exclude = {"orderItems"})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingFee;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(precision = 10, scale = 2)
    private BigDecimal netAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    private String shippingAddress;
    private String customerPhone;
    private String customerName;
    private String notes;
    private String trackingNumber;
    private String originalFileName;
    private String uploadedFilePath;

    /**
     * ⭐ CRITICAL: orphanRemoval = true หมายความว่า
     * - ถ้าลบ item จาก collection → JPA จะลบ item นั้นจาก DB
     * - ต้อง modify collection เดิม ห้าม assign collection ใหม่
     *
     * ✅ ถูก: order.getOrderItems().clear()
     *         order.getOrderItems().add(newItem)
     *
     * ❌ ผิด: order.setOrderItems(newList) → Error!
     */
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY,
            orphanRemoval = true  // ⭐ นี่คือสาเหตุของ error
    )
    private List<OrderItem> orderItems = new ArrayList<>();

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @PrePersist
    public void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();

        // Initialize default values
        if (status == null) status = OrderStatus.PENDING;
        if (paymentStatus == null) paymentStatus = PaymentStatus.UNPAID;
        if (shippingFee == null) shippingFee = BigDecimal.ZERO;
        if (discount == null) discount = BigDecimal.ZERO;
    }

    @PreUpdate
    public void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    /**
     * ✅ คำนวณยอดรวมจาก items
     */
    public void calculateTotals() {
        if (orderItems != null && !orderItems.isEmpty()) {
            // คำนวณยอดรวมสินค้า
            this.totalAmount = orderItems.stream()
                    .map(OrderItem::getTotalPrice)
                    .filter(price -> price != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // คำนวณยอดสุทธิ
            BigDecimal shippingAmount = this.shippingFee != null ? this.shippingFee : BigDecimal.ZERO;
            BigDecimal discountAmount = this.discount != null ? this.discount : BigDecimal.ZERO;
            this.netAmount = this.totalAmount.add(shippingAmount).subtract(discountAmount);

            // ป้องกันค่าติดลบ
            if (this.netAmount.compareTo(BigDecimal.ZERO) < 0) {
                this.netAmount = BigDecimal.ZERO;
            }
        } else {
            this.totalAmount = BigDecimal.ZERO;
            this.netAmount = BigDecimal.ZERO;
        }
    }

    // Enums
    public enum OrderSource {
        SHOP_24("24shop"),
        SHOPEE("Shopee"),
        MANUAL("Manual");

        private final String displayName;

        OrderSource(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
        PROCESSING,
        PACKED,
        SHIPPED,
        DELIVERED,
        CANCELLED,
        RETURNED
    }

    public enum PaymentStatus {
        UNPAID,
        PAID,
        REFUNDED
    }
}