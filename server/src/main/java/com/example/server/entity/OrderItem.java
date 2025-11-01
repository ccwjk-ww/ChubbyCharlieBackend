package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String productName;

    private String productSku;

    @Column(nullable = false)
    private Integer quantity;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal profit;

    private String notes;

    @Enumerated(EnumType.STRING)
    private StockDeductionStatus stockDeductionStatus = StockDeductionStatus.PENDING;

    @PrePersist
    @PreUpdate
    public void calculateTotals() {
        if (quantity != null && unitPrice != null) {
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal discountAmount = discount != null ? discount : BigDecimal.ZERO;
            this.totalPrice = subtotal.subtract(discountAmount);
        }

        if (quantity != null && costPerUnit != null) {
            this.totalCost = costPerUnit.multiply(BigDecimal.valueOf(quantity));

            if (totalPrice != null && totalCost != null) {
                this.profit = totalPrice.subtract(totalCost);
            }
        }
    }

    public enum StockDeductionStatus {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}