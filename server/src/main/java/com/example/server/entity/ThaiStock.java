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
        if (priceTotal == null || shippingCost == null) return BigDecimal.ZERO;

        BigDecimal total = priceTotal.add(shippingCost);
        if (Boolean.TRUE.equals(includeVat) && vatPercentage != null && vatPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal vat = total.multiply(vatPercentage).divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            total = total.add(vat);
        }
        return total.setScale(3, RoundingMode.HALF_UP);
    }

    // ============================================
    // calculateFinalPrice() — ราคา/หน่วย ก่อน VAT
    // ============================================
    @Override
    public BigDecimal calculateFinalPrice() {
        if (unitCostAtImport != null && unitCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return unitCostAtImport;
        }
        if (getQuantity() == null || getQuantity() == 0) return BigDecimal.ZERO;
        BigDecimal grandTotal = calculateTotalCost();
        return grandTotal.divide(BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    // ============================================
    // getAverageCostPerUnit() — ราคา/หน่วย ก่อน VAT
    // ============================================
    @Override
    public BigDecimal getAverageCostPerUnit() {
        if (unitCostAtImport != null && unitCostAtImport.compareTo(BigDecimal.ZERO) > 0) {
            return unitCostAtImport;
        }
        if (pricePerUnitWithShipping != null && pricePerUnitWithShipping.compareTo(BigDecimal.ZERO) > 0) {
            return pricePerUnitWithShipping;
        }
        if (pricePerUnit != null && pricePerUnit.compareTo(BigDecimal.ZERO) > 0) {
            return pricePerUnit;
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
    // Price calculation helpers
    // ============================================

    public BigDecimal calculatePricePerUnit() {
        if (priceTotal != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnit = priceTotal.divide(BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
        }
        return this.pricePerUnit;
    }

    public BigDecimal calculatePricePerUnitWithShipping() {
        this.pricePerUnitWithShipping = calculateFinalPrice();
        return this.pricePerUnitWithShipping;
    }

    // ============================================
    // @PrePersist @PreUpdate
    // ============================================
    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculatePricePerUnit();
        calculatePricePerUnitWithShipping();

        if (originalQuantity == null && getQuantity() != null) originalQuantity = getQuantity();
        if (unitCostAtImport == null) unitCostAtImport = calculateFinalPrice();
        if (totalCostAtImport == null) totalCostAtImport = calculateTotalCost();
        if (getDefectiveQuantity() == null) setDefectiveQuantity(0);
    }
}