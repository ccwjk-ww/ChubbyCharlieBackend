// ProductIngredient.java - อัปเดต Entity เดิม
package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
     * ⭐ Legacy: สำหรับกรณีใช้ Stock เดียว (Backward Compatible)
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_item_id")
    private StockBase stockItem;

    /**
     * ⭐ NEW: การจัดสรรสต็อกหลาย Lots
     */
    @OneToMany(mappedBy = "productIngredient", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("allocationPriority ASC")
    private List<ProductIngredientStockAllocation> stockAllocations = new ArrayList<>();

    /**
     * ระบุว่าใช้โหมดไหน: SINGLE หรือ MULTI_LOT
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_mode", nullable = false)
    private AllocationMode allocationMode = AllocationMode.SINGLE;

    @Column(precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalCost;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_type")
    private StockType stockType;

    public enum AllocationMode {
        SINGLE,      // ใช้ stock เดียว (แบบเดิม)
        MULTI_LOT    // ใช้หลาย lots (แบบใหม่)
    }

    public enum StockType {
        CHINA,
        THAI
    }

    @PrePersist
    @PreUpdate
    public void detectStockType() {
        if (allocationMode == AllocationMode.SINGLE && stockItem != null) {
            if (stockItem instanceof ChinaStock) {
                this.stockType = StockType.CHINA;
            } else if (stockItem instanceof ThaiStock) {
                this.stockType = StockType.THAI;
            }
        }
    }

    /**
     * ⭐ Helper: เพิ่มการจัดสรรสต็อก
     */
    public void addStockAllocation(StockBase stock, BigDecimal quantity, Integer priority, BigDecimal costPerUnit) {
        ProductIngredientStockAllocation allocation = new ProductIngredientStockAllocation();
        allocation.setProductIngredient(this);
        allocation.setStockItem(stock);
        allocation.setAllocatedQuantity(quantity);
        allocation.setAllocationPriority(priority);
        allocation.setCostPerUnit(costPerUnit);

        this.stockAllocations.add(allocation);
        this.allocationMode = AllocationMode.MULTI_LOT;
    }

    /**
     * ⭐ Helper: ลบการจัดสรรทั้งหมด
     */
    public void clearStockAllocations() {
        this.stockAllocations.clear();
    }

    /**
     * ⭐ Helper: คำนวณต้นทุนรวมจากทุก allocations
     */
    public BigDecimal calculateTotalCostFromAllocations() {
        if (allocationMode == AllocationMode.MULTI_LOT) {
            return stockAllocations.stream()
                    .map(ProductIngredientStockAllocation::getTotalCost)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return totalCost != null ? totalCost : BigDecimal.ZERO;
    }
}