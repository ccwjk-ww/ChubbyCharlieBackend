package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
@Table(name = "thai_stock")
@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "stock_item_id")
public class ThaiStock extends StockBase {

    @Column(precision = 10, scale = 3)
    private BigDecimal priceTotal;

    @Column(precision = 10, scale = 3)
    private BigDecimal shippingCost;

    @Column(precision = 10, scale = 3)
    private BigDecimal pricePerUnit;

    @Column(precision = 10, scale = 3)
    private BigDecimal pricePerUnitWithShipping;

    @Column(precision = 5, scale = 2)
    private BigDecimal bufferPercentage = BigDecimal.ZERO;

    @Column
    private Boolean includeBuffer = false;

    // ⭐ ฟิลด์เดิมที่มีอยู่แล้ว (จากโค้ดเดิม)
    @Column(name = "original_quantity")
    private Integer originalQuantity;

    @Column(precision = 10, scale = 3, name = "unit_cost_at_import")
    private BigDecimal unitCostAtImport;

    @Column(precision = 10, scale = 3, name = "total_cost_at_import")
    private BigDecimal totalCostAtImport;

    // ============================================
    // ⭐ เพิ่ม: Quantity Management Methods
    // ============================================

    /**
     * ✅ ดึงจำนวนสินค้าทั้งหมด (ตอนนำเข้า)
     */
    public Integer getOriginalQuantity() {
        return originalQuantity != null ? originalQuantity : 0;
    }

    /**
     * ✅ ดึงจำนวนสินค้าคงเหลือ (ปัจจุบัน)
     */
    public Integer getCurrentQuantity() {
        return getQuantity() != null ? getQuantity() : 0;
    }

    /**
     * ✅ คำนวณจำนวนสินค้าที่ใช้ไป
     */
    public Integer getUsedQuantity() {
        int original = getOriginalQuantity();
        int current = getCurrentQuantity();
        return original - current;
    }

    /**
     * ✅ คำนวณเปอร์เซ็นต์การใช้งาน
     */
    public BigDecimal getUsagePercentage() {
        int original = getOriginalQuantity();
        if (original <= 0) {
            return BigDecimal.ZERO;
        }

        int used = getUsedQuantity();
        return BigDecimal.valueOf(used)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(original), 2, RoundingMode.HALF_UP);
    }

    /**
     * ✅ คำนวณเปอร์เซ็นต์ที่เหลือ
     */
    public BigDecimal getRemainingPercentage() {
        return BigDecimal.valueOf(100).subtract(getUsagePercentage());
    }

    // ============================================
    // แก้ไข: calculateTotalCost() - ใช้ค่าคงที่
    // ============================================
    @Override
    public BigDecimal calculateTotalCost() {
        // ถ้ามีค่า totalCostAtImport แล้ว ใช้ค่าคงที่นั้น
        if (totalCostAtImport != null && totalCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return totalCostAtImport;
        }

        // ถ้ายังไม่มี ให้คำนวณ (กรณี Preview/Edit)
        if (priceTotal == null || shippingCost == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = priceTotal.add(shippingCost);

        if (Boolean.TRUE.equals(includeBuffer) && bufferPercentage != null && bufferPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal buffer = total.multiply(bufferPercentage).divide(
                    BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            total = total.add(buffer);
        }

        return total.setScale(3, RoundingMode.HALF_UP);
    }

    // ============================================
    // แก้ไข: calculateFinalPrice() - ใช้ค่าคงที่
    // ============================================
    @Override
    public BigDecimal calculateFinalPrice() {
        // ถ้ามีค่า unitCostAtImport แล้ว ใช้ค่าคงที่นั้น
        if (unitCostAtImport != null && unitCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return unitCostAtImport;
        }

        // ถ้ายังไม่มี ให้คำนวณ (กรณี Preview/Edit)
        if (getQuantity() == null || getQuantity() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal grandTotal = calculateTotalCost();
        return grandTotal.divide(
                BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    public BigDecimal calculatePricePerUnit() {
        if (priceTotal != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnit = priceTotal.divide(
                    BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
        }
        return this.pricePerUnit;
    }

    public BigDecimal calculatePricePerUnitWithShipping() {
        this.pricePerUnitWithShipping = calculateFinalPrice();
        return this.pricePerUnitWithShipping;
    }

    // ============================================
    // แก้ไข: @PrePersist @PreUpdate
    // ============================================
    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculatePricePerUnit();
        calculatePricePerUnitWithShipping();

        // ⭐ บันทึกค่าต้นทุนตอนนำเข้า (ครั้งแรกเท่านั้น)
        if (originalQuantity == null && getQuantity() != null) {
            originalQuantity = getQuantity();
        }

        if (unitCostAtImport == null) {
            unitCostAtImport = calculateFinalPrice();
        }

        if (totalCostAtImport == null) {
            totalCostAtImport = calculateTotalCost();
        }
    }
}