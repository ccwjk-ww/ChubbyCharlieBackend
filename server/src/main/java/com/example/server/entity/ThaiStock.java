// ============================================
// ThaiStock.java - แก้ไข
// ============================================
package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
@Table(name = "thai_stock") // ⭐ ชื่อตารางชัดเจน
@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "stock_item_id") // ⭐ สำคัญ! เชื่อม FK กับ parent
public class ThaiStock extends StockBase {

    // ⭐ ลบ quantity ออก (ใช้จาก parent แทน)
    // private Integer quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal priceTotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(precision = 10, scale = 2)
    private BigDecimal pricePerUnitWithShipping;

    @Override
    public BigDecimal calculateTotalCost() {
        if (priceTotal == null || shippingCost == null) {
            return BigDecimal.ZERO;
        }
        return priceTotal.add(shippingCost);
    }

    @Override
    public BigDecimal calculateFinalPrice() {
        return pricePerUnitWithShipping != null ?
                pricePerUnitWithShipping : BigDecimal.ZERO;
    }

    public BigDecimal calculatePricePerUnit() {
        if (priceTotal != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnit = priceTotal.divide(
                    BigDecimal.valueOf(getQuantity()),
                    2,
                    RoundingMode.HALF_UP
            );
        }
        return this.pricePerUnit;
    }

    public BigDecimal calculatePricePerUnitWithShipping() {
        BigDecimal totalCost = calculateTotalCost();
        if (totalCost.compareTo(BigDecimal.ZERO) > 0 &&
                getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnitWithShipping = totalCost.divide(
                    BigDecimal.valueOf(getQuantity()),
                    2,
                    RoundingMode.HALF_UP
            );
        }
        return this.pricePerUnitWithShipping;
    }

    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculatePricePerUnit();
        calculatePricePerUnitWithShipping();
    }
}