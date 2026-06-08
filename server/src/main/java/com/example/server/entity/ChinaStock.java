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
    private BigDecimal vatPercentage = BigDecimal.ZERO;

    @Column
    private Boolean includeVat = false;

    @Column(name = "original_quantity")
    private Integer originalQuantity;

    @Column(precision = 10, scale = 3, name = "unit_cost_at_import")
    private BigDecimal unitCostAtImport;

    @Column(precision = 10, scale = 3, name = "total_cost_at_import")
    private BigDecimal totalCostAtImport;

    // ============================================
    // Quantity Management Methods
    // ============================================

    public Integer getOriginalQuantity() {
        return originalQuantity != null ? originalQuantity : 0;
    }

    public Integer getCurrentQuantity() {
        return getQuantity() != null ? getQuantity() : 0;
    }

    public Integer getUsedQuantity() {
        return getOriginalQuantity() - getCurrentQuantity();
    }

    public BigDecimal getUsagePercentage() {
        int original = getOriginalQuantity();
        if (original <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(getUsedQuantity())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(original), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getRemainingPercentage() {
        return BigDecimal.valueOf(100).subtract(getUsagePercentage());
    }

    // ============================================
    // calculateTotalCost()
    // ============================================
    @Override
    public BigDecimal calculateTotalCost() {
        if (totalCostAtImport != null && totalCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return totalCostAtImport;
        }
        if (totalBath == null) return BigDecimal.ZERO;

        BigDecimal total = totalBath;
        if (Boolean.TRUE.equals(includeVat) && vatPercentage != null && vatPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal vat = total.multiply(vatPercentage).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            total = total.add(vat);
        }
        return total.setScale(3, RoundingMode.HALF_UP);
    }

    // ============================================
// ⭐ แก้: calculateFinalPrice ใช้ originalQuantity
// ============================================
    @Override
    public BigDecimal calculateFinalPrice() {
        if (unitCostAtImport != null && unitCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return unitCostAtImport;
        }
        // ⭐ ใช้ originalQuantity เพื่อให้ราคา/หน่วยไม่เปลี่ยนแปลงเมื่อตัดสต็อก
        int qty = (originalQuantity != null && originalQuantity > 0)
                ? originalQuantity
                : (getQuantity() != null ? getQuantity() : 0);
        if (qty == 0) return BigDecimal.ZERO;
        return calculateTotalCostBeforeVat()
                .divide(BigDecimal.valueOf(qty), 3, RoundingMode.HALF_UP);
    }

    /**
     * Helper: คำนวณ totalBath (ก่อน VAT)
     */
    private BigDecimal calculateTotalCostBeforeVat() {
        if (totalBath != null) return totalBath;
        return BigDecimal.ZERO;
    }

    // ============================================
    // getAverageCostPerUnit() — ราคา/หน่วย ก่อน VAT
    // ============================================
    @Override
    public BigDecimal getAverageCostPerUnit() {
        if (unitCostAtImport != null && unitCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return unitCostAtImport;
        }
        if (finalPricePerPair != null && finalPricePerPair.compareTo(BigDecimal.ZERO) > 0) {
            return finalPricePerPair;
        }
        if (pricePerUnitBath != null && pricePerUnitBath.compareTo(BigDecimal.ZERO) > 0) {
            return pricePerUnitBath;
        }
        return calculateFinalPrice();
    }

    // ============================================
    // ⭐ getAverageCostPerUnitWithVat() — ราคา/หน่วย รวม VAT (ถ้ามี)
    // ใช้สำหรับคำนวณมูลค่าของเสีย
    // ============================================
    @Override
    public BigDecimal getAverageCostPerUnitWithVat() {
        BigDecimal baseUnit = getAverageCostPerUnit();
        if (baseUnit == null || baseUnit.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        // ถ้าไม่มี VAT → คืนราคาปกติ
        if (!Boolean.TRUE.equals(includeVat) || vatPercentage == null || vatPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return baseUnit;
        }

        // มี VAT: baseUnit × (1 + vatPct/100)
        BigDecimal vatMultiplier = BigDecimal.ONE.add(
                vatPercentage.divide(BigDecimal.valueOf(100), 5, RoundingMode.HALF_UP)
        );
        return baseUnit.multiply(vatMultiplier).setScale(3, RoundingMode.HALF_UP);
    }

    // ============================================
    // Calculation helpers
    // ============================================

    // ============================================
// ⭐ แก้: ใช้ originalQuantity ใน calculateTotalValueYuan
// ============================================
    public BigDecimal calculateTotalValueYuan() {
        // ถ้า lock แล้ว ไม่ recalculate
        if (totalCostAtImport != null && totalCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return this.totalValueYuan;
        }
        // ⭐ ใช้ originalQuantity ถ้ามี มิฉะนั้นใช้ quantity ปัจจุบัน (ครั้งแรกที่สร้าง)
        int qty = (originalQuantity != null && originalQuantity > 0)
                ? originalQuantity
                : (getQuantity() != null ? getQuantity() : 0);
        if (unitPriceYuan != null && qty > 0) {
            this.totalValueYuan = unitPriceYuan.multiply(BigDecimal.valueOf(qty))
                    .setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalValueYuan;
    }

    public BigDecimal calculateTotalYuan() {
        // ถ้า lock แล้ว ไม่ recalculate
        if (totalCostAtImport != null && totalCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return this.totalYuan;
        }
        if (totalValueYuan != null && shippingWithinChinaYuan != null) {
            this.totalYuan = totalValueYuan.add(shippingWithinChinaYuan).setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalYuan;
    }

    public BigDecimal calculateTotalBath() {
        // ถ้า lock แล้ว ไม่ recalculate
        if (totalCostAtImport != null && totalCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return this.totalBath;
        }
        if (totalYuan != null && exchangeRate != null) {
            BigDecimal total = totalYuan.multiply(exchangeRate);
            if (shippingChinaToThaiBath != null) total = total.add(shippingChinaToThaiBath);
            this.totalBath = total.setScale(3, RoundingMode.HALF_UP);
        }
        return this.totalBath;
    }

    // ============================================
// ⭐ แก้: calculatePricePerUnitBath ใช้ originalQuantity
// ============================================
    public BigDecimal calculatePricePerUnitBath() {
        // ⭐ ใช้ originalQuantity แทน quantity เพื่อให้ราคา/หน่วยถูกต้องเสมอ
        int qty = (originalQuantity != null && originalQuantity > 0)
                ? originalQuantity
                : (getQuantity() != null ? getQuantity() : 0);
        if (totalBath != null && qty > 0) {
            this.pricePerUnitBath = totalBath.divide(BigDecimal.valueOf(qty), 3, RoundingMode.HALF_UP);
        }
        return this.pricePerUnitBath;
    }

    public BigDecimal calculateAvgShippingPerPair() {
        if (shippingChinaToThaiBath == null || getQuantity() == null || getQuantity() == 0) return BigDecimal.ZERO;
        return shippingChinaToThaiBath.divide(BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalBath() { return this.totalBath; }
    public BigDecimal getFinalPricePerPair() { return this.finalPricePerPair; }
    public BigDecimal getAvgShippingPerPair() { return calculateAvgShippingPerPair(); }


    // ============================================
// @PrePersist @PreUpdate — ⭐ แก้: lock unitCostAtImport และ totalCostAtImport
// ============================================
    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculateTotalValueYuan();
        calculateTotalYuan();
        calculateTotalBath();
        calculatePricePerUnitBath();

        // ⭐ originalQuantity: set ครั้งแรกเท่านั้น ไม่ update ภายหลัง
        if (originalQuantity == null && getQuantity() != null) {
            originalQuantity = getQuantity();
        }

        // ⭐ unitCostAtImport และ totalCostAtImport: lock ไว้ครั้งแรก ไม่ recalculate
        // เพราะถ้า recalculate จะใช้ quantity ปัจจุบัน (ที่ถูกตัดไปแล้ว) ทำให้ผิดพลาด
        if (unitCostAtImport == null) {
            unitCostAtImport = calculateFinalPrice();
        }
        if (totalCostAtImport == null) {
            totalCostAtImport = calculateTotalCost();
        }

        this.finalPricePerPair = getAverageCostPerUnit(); // ⭐ ใช้ unitCostAtImport ที่ lock ไว้

        if (getDefectiveQuantity() == null) setDefectiveQuantity(0);
    }
}