package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "product_ingredients")
public class ProductIngredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ingredientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private String ingredientName;

    @Column(nullable = false, precision = 10, scale = 4)
    private BigDecimal requiredQuantity;

    @Column(nullable = false)
    private String unit;

    /**
     * ⭐ CRITICAL: ใช้ ManyToOne กับ StockBase แต่ต้อง eager fetch
     * เพื่อให้ JPA โหลด subclass ที่ถูกต้อง (ChinaStock หรือ ThaiStock)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_item_id")
    private StockBase stockItem;

    /**
     * ✅ เพิ่ม: Cost per unit (ราคาต่อหน่วยจาก Stock)
     * คำนวณโดย ProductCostCalculationService
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    /**
     * ✅ เพิ่ม: Total cost (ต้นทุนรวมของ ingredient นี้)
     * = costPerUnit × requiredQuantity
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal totalCost;

    /**
     * ✅ เพิ่ม: Notes (หมายเหตุเพิ่มเติม)
     */
    @Column(length = 500)
    private String notes;

    /**
     * ⭐ เพิ่ม: เก็บ type เพื่อความชัดเจน
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stock_type")
    private StockType stockType;

    public enum StockType {
        CHINA,
        THAI
    }

    /**
     * ⭐ Auto-detect stock type เมื่อบันทึก
     */
    @PrePersist
    @PreUpdate
    public void detectStockType() {
        if (stockItem != null) {
            if (stockItem instanceof ChinaStock) {
                this.stockType = StockType.CHINA;
            } else if (stockItem instanceof ThaiStock) {
                this.stockType = StockType.THAI;
            }
        }
    }

    /**
     * ✅ เพิ่ม: Helper method สำหรับคำนวณต้นทุน
     * (จะถูกเรียกจาก ProductCostCalculationService)
     */
    public void calculateCost(BigDecimal unitCost) {
        if (unitCost != null && requiredQuantity != null) {
            this.costPerUnit = unitCost;
            this.totalCost = unitCost.multiply(requiredQuantity);
        }
    }
}