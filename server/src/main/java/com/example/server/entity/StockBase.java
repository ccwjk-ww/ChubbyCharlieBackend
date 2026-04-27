package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Data
@Table(name = "stock_base")
@Inheritance(strategy = InheritanceType.JOINED)
@EqualsAndHashCode
@ToString
public abstract class StockBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockItemId;

    @Column(nullable = false)
    private String name;

    private LocalDateTime lotDate;
    private String shopURL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StockStatus status;

    @Column(name = "stock_lot_id")
    private Long stockLotId;

    @Column(name = "quantity")
    private Integer quantity;

    /**
     * จำนวนของเสีย (Defective Quantity)
     */
    @Column(name = "defective_quantity")
    private Integer defectiveQuantity = 0;

    public enum StockStatus {
        ACTIVE,
        INACTIVE,
        OUT_OF_STOCK
    }

    // ============================================
    // Abstract Methods
    // ============================================

    public abstract BigDecimal calculateTotalCost();
    public abstract BigDecimal calculateFinalPrice();
    public abstract BigDecimal getAverageCostPerUnit();

    /**
     * ⭐ NEW: ราคาต้นทุนต่อหน่วย "รวม VAT" (ถ้ามี)
     * ถ้าไม่มี VAT → คืนค่าเดียวกับ getAverageCostPerUnit()
     * subclass override ได้ถ้าต้องการ
     */
    public BigDecimal getAverageCostPerUnitWithVat() {
        // Default: ถ้า subclass ไม่ override ให้ใช้ราคาปกติ
        return getAverageCostPerUnit();
    }

    // ============================================
    // Helper Methods
    // ============================================

    @Transient
    public String getStockType() {
        if (this instanceof ChinaStock) return "CHINA";
        else if (this instanceof ThaiStock) return "THAI";
        return "UNKNOWN";
    }

    @Transient
    public boolean isOutOfStock() {
        return quantity == null || quantity <= 0;
    }

    @Transient
    public boolean isLowStock(int threshold) {
        return quantity != null && quantity <= threshold;
    }

    // ============================================
    // Defective Stock Methods
    // ============================================

    public Integer getDefectiveQuantity() {
        return defectiveQuantity != null ? defectiveQuantity : 0;
    }

    /**
     * ⭐ คำนวณมูลค่าของเสีย
     * ใช้ราคารวม VAT ถ้าสินค้านั้นมี VAT
     * ใช้ราคาปกติถ้าไม่มี VAT
     */
    @Transient
    public BigDecimal getDefectiveValue() {
        int qty = getDefectiveQuantity();
        if (qty <= 0) return BigDecimal.ZERO;

        // ใช้ getAverageCostPerUnitWithVat() ซึ่ง subclass จะ override ให้รวม VAT ถ้ามี
        BigDecimal unitCost = getAverageCostPerUnitWithVat();
        if (unitCost == null || unitCost.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        return unitCost.multiply(BigDecimal.valueOf(qty))
                .setScale(3, RoundingMode.HALF_UP);
    }
}