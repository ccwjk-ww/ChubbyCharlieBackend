package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
@Table(name = "china_stock")
@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "stock_item_id")
public class ChinaStock extends StockBase {

    @Column(precision = 10, scale = 3)
    private BigDecimal unitPriceYuan;

    @Column(precision = 10, scale = 3)
    private BigDecimal totalValueYuan;

    @Column(precision = 10, scale = 3)
    private BigDecimal shippingWithinChinaYuan;

    @Column(precision = 10, scale = 3)
    private BigDecimal totalYuan;

    @Column(precision = 10, scale = 3)
    private BigDecimal totalBath;

    @Column(precision = 10, scale = 3)
    private BigDecimal pricePerUnitBath;

    @Column(precision = 10, scale = 3)
    private BigDecimal shippingChinaToThaiBath;

    @Column(precision = 10, scale = 3)
    private BigDecimal finalPricePerPair;

    @Column(precision = 10, scale = 3)
    private BigDecimal exchangeRate;

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
    // การคำนวณแบบเดิม (ใช้สำหรับ Preview/Edit เท่านั้น)
    // ============================================

    public BigDecimal calculateTotalValueYuan() {
        if (unitPriceYuan != null && getQuantity() != null) {
            this.totalValueYuan = unitPriceYuan
                    .multiply(BigDecimal.valueOf(getQuantity()))
                    .setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalValueYuan;
    }

    public BigDecimal calculateTotalYuan() {
        if (totalValueYuan != null && shippingWithinChinaYuan != null) {
            this.totalYuan = totalValueYuan.add(shippingWithinChinaYuan)
                    .setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalYuan;
    }

    public BigDecimal calculateTotalBath() {
        if (totalYuan != null && exchangeRate != null) {
            BigDecimal total = totalYuan.multiply(exchangeRate);

            if (shippingChinaToThaiBath != null) {
                total = total.add(shippingChinaToThaiBath);
            }

            this.totalBath = total.setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalBath;
    }

    public BigDecimal calculatePricePerUnitBath() {
        if (totalBath != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnitBath = totalBath.divide(
                    BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
        }
        return this.pricePerUnitBath;
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
        if (totalBath == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = totalBath;

        if (Boolean.TRUE.equals(includeBuffer) &&
                bufferPercentage != null &&
                bufferPercentage.compareTo(BigDecimal.ZERO) > 0) {

            BigDecimal buffer = total.multiply(bufferPercentage)
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
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

    public BigDecimal calculateAvgShippingPerPair() {
        if (shippingChinaToThaiBath == null || getQuantity() == null || getQuantity() == 0) {
            return BigDecimal.ZERO;
        }
        return shippingChinaToThaiBath.divide(
                BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalBath() {
        return this.totalBath;
    }

    public BigDecimal getFinalPricePerPair() {
        return this.finalPricePerPair;
    }

    public BigDecimal getAvgShippingPerPair() {
        return calculateAvgShippingPerPair();
    }

    // ============================================
    // แก้ไข: @PrePersist @PreUpdate
    // ============================================
    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculateTotalValueYuan();
        calculateTotalYuan();
        calculateTotalBath();
        calculatePricePerUnitBath();
        this.finalPricePerPair = calculateFinalPrice();

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