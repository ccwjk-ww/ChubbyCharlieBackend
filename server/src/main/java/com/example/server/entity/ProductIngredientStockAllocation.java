// ProductIngredientStockAllocation.java - Entity ใหม่สำหรับจัดการการจัดสรรสต็อก
package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "product_ingredient_stock_allocations")
public class ProductIngredientStockAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long allocationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private ProductIngredient productIngredient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockBase stockItem;

    /**
     * จำนวนที่จัดสรรจาก stock นี้
     */
    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal allocatedQuantity;

    /**
     * ลำดับการตัดสต็อก (1 = ตัดก่อน, 2 = ตัดที่สอง, ...)
     */
    @Column(nullable = false)
    private Integer allocationPriority;

    /**
     * ต้นทุนต่อหน่วยในขณะที่จัดสรร
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    /**
     * ต้นทุนรวมของการจัดสรรนี้
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_type")
    private StockType stockType;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    public enum StockType {
        CHINA,
        THAI
    }

    @PrePersist
    public void onCreate() {
        createdDate = LocalDateTime.now();
        detectStockType();
        calculateCost();
    }

    @PreUpdate
    public void onUpdate() {
        detectStockType();
        calculateCost();
    }

    private void detectStockType() {
        if (stockItem != null) {
            if (stockItem instanceof ChinaStock) {
                this.stockType = StockType.CHINA;
            } else if (stockItem instanceof ThaiStock) {
                this.stockType = StockType.THAI;
            }
        }
    }

    private void calculateCost() {
        if (costPerUnit != null && allocatedQuantity != null) {
            this.totalCost = costPerUnit.multiply(allocatedQuantity);
        }
    }
    // ⭐ เพิ่ม: transient fields สำหรับ API response
    @Transient
    public Long getStockItemId() {
        return stockItem != null ? stockItem.getStockItemId() : null;
    }

    @Transient
    public String getStockItemName() {
        return stockItem != null ? stockItem.getName() : null;
    }

    @Transient
    public String getStockType() {
        if (stockItem instanceof ChinaStock) return "CHINA";
        if (stockItem instanceof ThaiStock) return "THAI";
        return null;
    }

    @Transient
    public String getLotName() {
        return stockItem != null ? stockItem.getName() : null;
    }

    @Transient
    public Integer getAvailableQuantity() {
        return stockItem != null ? stockItem.getQuantity() : null;
    }
}