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

    // ============================================
    // ⭐ 1. calculateTotalValueYuan - ไม่ต้องแก้
    // ============================================
    public BigDecimal calculateTotalValueYuan() {
        if (unitPriceYuan != null && getQuantity() != null) {
            this.totalValueYuan = unitPriceYuan
                    .multiply(BigDecimal.valueOf(getQuantity()))
                    .setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalValueYuan;
    }

    // ============================================
    // ⭐ 2. calculateTotalYuan - ไม่ต้องแก้
    // ============================================
    public BigDecimal calculateTotalYuan() {
        if (totalValueYuan != null && shippingWithinChinaYuan != null) {
            this.totalYuan = totalValueYuan.add(shippingWithinChinaYuan)
                    .setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalYuan;
    }

    // ============================================
    // ⭐ 3. calculateTotalBath - แก้ไข (รวมค่าส่ง)
    // ============================================
    public BigDecimal calculateTotalBath() {
        if (totalYuan != null && exchangeRate != null) {
            // แปลงหยวนเป็นบาท
            BigDecimal total = totalYuan.multiply(exchangeRate);

            // ⭐ บวกค่าส่งจีน-ไทย
            if (shippingChinaToThaiBath != null) {
                total = total.add(shippingChinaToThaiBath);
            }

            this.totalBath = total.setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalBath;
    }

    // ============================================
    // ⭐ 4. calculatePricePerUnitBath - ไม่ต้องแก้
    // ============================================
    public BigDecimal calculatePricePerUnitBath() {
        if (totalBath != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnitBath = totalBath.divide(
                    BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
        }
        return this.pricePerUnitBath;
    }

    // ============================================
    // ⭐ 5. calculateTotalCost - แก้ไข (เพิ่มแค่ Buffer)
    // ============================================
    @Override
    public BigDecimal calculateTotalCost() {
        if (totalBath == null) {
            return BigDecimal.ZERO;
        }

        // ⭐ totalBath รวมค่าส่งแล้ว
        BigDecimal total = totalBath;

        // ⭐ เพิ่มแค่ Buffer
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
    // ⭐ 6. calculateFinalPrice - แก้ไข
    // ============================================
    @Override
    public BigDecimal calculateFinalPrice() {
        if (getQuantity() == null || getQuantity() == 0) {
            return BigDecimal.ZERO;
        }

        // ⭐ ใช้ calculateTotalCost() ที่รวม Buffer แล้ว
        BigDecimal grandTotal = calculateTotalCost();
        return grandTotal.divide(
                BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    // ============================================
    // ⭐ 7. calculateAvgShippingPerPair - ไม่ต้องแก้
    // ============================================
    public BigDecimal calculateAvgShippingPerPair() {
        if (shippingChinaToThaiBath == null || getQuantity() == null || getQuantity() == 0) {
            return BigDecimal.ZERO;
        }
        return shippingChinaToThaiBath.divide(
                BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    // ============================================
    // ⭐ 8. Getters - แก้ไข
    // ============================================

    /**
     * ⭐ getTotalBath() คืนค่าจาก field (ไม่ใช่ calculateTotalCost)
     */
    public BigDecimal getTotalBath() {
        return this.totalBath; // ⭐ คืนค่าจาก field
    }

    /**
     * ⭐ getFinalPricePerPair() คืนค่าจาก field (ไม่ใช่ calculateFinalPrice)
     */
    public BigDecimal getFinalPricePerPair() {
        return this.finalPricePerPair; // ⭐ คืนค่าจาก field
    }

    /**
     * Backward compatibility
     */
    public BigDecimal getAvgShippingPerPair() {
        return calculateAvgShippingPerPair();
    }

    // ============================================
    // ⭐ 9. @PrePersist @PreUpdate - ไม่ต้องแก้
    // ============================================
    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculateTotalValueYuan();
        calculateTotalYuan();
        calculateTotalBath();
        calculatePricePerUnitBath();
        this.finalPricePerPair = calculateFinalPrice();
    }
}