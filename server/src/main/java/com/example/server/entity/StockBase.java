// ============================================
// StockBase.java - แก้ไข
// ============================================
package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Data
@Table(name = "stock_base") // ⭐ กำหนดชื่อตารางชัดเจน
@Inheritance(strategy = InheritanceType.JOINED)
@EqualsAndHashCode
@ToString
public abstract class StockBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockItemId;

    @Column(nullable = false) // ⭐ เพิ่ม constraint
    private String name;

    private LocalDateTime lotDate;
    private String shopURL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private StockStatus status;

    @Column(name = "stock_lot_id")
    private Long stockLotId;

    // ⭐ เพิ่ม: Quantity เป็น field ร่วม (มีทั้ง ChinaStock และ ThaiStock)
    @Column(name = "quantity")
    private Integer quantity;

    public enum StockStatus {
        ACTIVE,
        INACTIVE,
        OUT_OF_STOCK
    }

    // Abstract methods
    public abstract BigDecimal calculateTotalCost();
    public abstract BigDecimal calculateFinalPrice();

    // ⭐ เพิ่ม: Helper method สำหรับ check subclass
    @Transient
    public String getStockType() {
        if (this instanceof ChinaStock) {
            return "CHINA";
        } else if (this instanceof ThaiStock) {
            return "THAI";
        }
        return "UNKNOWN";
    }
}