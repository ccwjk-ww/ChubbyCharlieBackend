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

    // ⭐ Buffer fields
    @Column(precision = 5, scale = 2)
    private BigDecimal bufferPercentage = BigDecimal.ZERO;

    @Column
    private Boolean includeBuffer = false;

    /**
     * ⭐ แก้ไข: calculateTotalCost() คืนค่า Grand Total (รวม Buffer)
     * ค่านี้จะถูกใช้แทน "Total" ในตาราง
     */
    @Override
    public BigDecimal calculateTotalCost() {
        if (priceTotal == null || shippingCost == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = priceTotal.add(shippingCost);

        // ⭐ เพิ่ม Buffer
        if (Boolean.TRUE.equals(includeBuffer) && bufferPercentage != null && bufferPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal buffer = total.multiply(bufferPercentage).divide(
                    BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
            total = total.add(buffer);
        }

        return total.setScale(3, RoundingMode.HALF_UP);
    }

    /**
     * ⭐ แก้ไข: calculateFinalPrice() คืนค่า Final Price Per Unit (รวม Buffer)
     * ค่านี้จะถูกใช้เป็น "Final Price/Unit" ในตาราง
     */
    @Override
    public BigDecimal calculateFinalPrice() {
        if (getQuantity() == null || getQuantity() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal grandTotal = calculateTotalCost(); // ใช้ Grand Total
        return grandTotal.divide(
                BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
    }

    /**
     * คำนวณ Price Per Unit (ไม่รวมค่าส่ง)
     */
    public BigDecimal calculatePricePerUnit() {
        if (priceTotal != null && getQuantity() != null && getQuantity() > 0) {
            this.pricePerUnit = priceTotal.divide(
                    BigDecimal.valueOf(getQuantity()), 3, RoundingMode.HALF_UP);
        }
        return this.pricePerUnit;
    }

    /**
     * ⭐ คำนวณ Price Per Unit With Shipping (รวมทุกอย่าง รวม Buffer)
     */
    public BigDecimal calculatePricePerUnitWithShipping() {
        this.pricePerUnitWithShipping = calculateFinalPrice(); // ใช้ calculateFinalPrice()
        return this.pricePerUnitWithShipping;
    }

    @PrePersist
    @PreUpdate
    public void calculateFields() {
        calculatePricePerUnit();
        calculatePricePerUnitWithShipping();
    }
}