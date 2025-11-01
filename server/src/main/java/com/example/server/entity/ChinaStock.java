// ============================================
// ChinaStock.java - แก้ไข
// ============================================
package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
@Table(name = "china_stock") // ⭐ ชื่อตารางชัดเจน
@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "stock_item_id") // ⭐ สำคัญ! เชื่อม FK กับ parent
public class ChinaStock extends StockBase {

    @Column(precision = 10, scale = 2)
    private BigDecimal unitPriceYuan;

    // ⭐ ลบ quantity ออก (ใช้จาก parent แทน)
    // private Integer quantity; 

    @Column(precision = 10, scale = 2)
    private BigDecimal totalValueYuan;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingWithinChinaYuan;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalYuan;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalBath;

    @Column(precision = 10, scale = 2)
    private BigDecimal pricePerUnitBath;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingChinaToThaiBath;

    @Column(precision = 10, scale = 2)
    private BigDecimal avgShippingPerPair;

    @Column(precision = 10, scale = 2)
    private BigDecimal finalPricePerPair;

    @Column(precision = 10, scale = 2)
    private BigDecimal exchangeRate;

    @Override
    public BigDecimal calculateTotalCost() {
        if (totalBath == null || avgShippingPerPair == null || getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return totalBath.add(avgShippingPerPair.multiply(BigDecimal.valueOf(getQuantity())));
    }

    @Override
    public BigDecimal calculateFinalPrice() {
        if (pricePerUnitBath == null || avgShippingPerPair == null) {
            return BigDecimal.ZERO;
        }
        return pricePerUnitBath.add(avgShippingPerPair);
    }

    public BigDecimal calculateTotalValueYuan() {
        if (unitPriceYuan != null && getQuantity() != null) {
            this.totalValueYuan = unitPriceYuan.multiply(BigDecimal.valueOf(getQuantity()));
        }
        return this.totalValueYuan;
    }

    public BigDecimal calculateTotalYuan() {
        if (totalValueYuan != null && shippingWithinChinaYuan != null) {
            this.totalYuan = totalValueYuan.add(shippingWithinChinaYuan);
        }
        return this.totalYuan;
    }

    public BigDecimal calculateTotalBath() {
        if (totalYuan != null && exchangeRate != null) {
            this.totalBath = totalYuan.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
        }
        return this.totalBath;
    }

    public BigDecimal calculatePricePerUnitBath() {
        if (totalBath != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnitBath = totalBath.divide(
                    BigDecimal.valueOf(getQuantity()),
                    2,
                    RoundingMode.HALF_UP
            );
        }
        return this.pricePerUnitBath;
    }

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
