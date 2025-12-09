package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "stock_forecasts")
public class StockForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long forecastId;

    // ⭐ แก้ไข: เพิ่ม orphanRemoval เพื่อให้ลบ forecast เมื่อ stock ถูกลบ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockBase stockItem;

    @Column(nullable = false)
    private String stockItemName;

    @Column(nullable = false)
    private String stockType; // "CHINA" or "THAI"

    // ข้อมูล Stock ปัจจุบัน
    @Column(nullable = false)
    private Integer currentStock;

    @Column(precision = 10, scale = 2)
    private BigDecimal currentStockValue;

    // การวิเคราะห์ความต้องการ
    @Column(nullable = false)
    private Integer averageDailyUsage; // ใช้เฉลี่ยต่อวัน

    @Column(nullable = false)
    private Integer averageWeeklyUsage; // ใช้เฉลี่ยต่อสัปดาห์

    @Column(nullable = false)
    private Integer averageMonthlyUsage; // ใช้เฉลี่ยต่อเดือน

    // การคาดการณ์
    @Column(nullable = false)
    private Integer daysUntilStockOut; // จำนวนวันที่จะหมด stock

    @Column(nullable = false)
    private LocalDateTime estimatedStockOutDate; // วันที่คาดว่าจะหมด stock

    // คำแนะนำการสั่งซื้อ
    @Column(nullable = false)
    private Integer recommendedOrderQuantity; // แนะนำให้สั่งซื้อจำนวนเท่าไหร่

    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedOrderCost; // ค่าใช้จ่ายที่คาดว่าจะต้องสั่งซื้อ

    // สถานะความเร่งด่วน
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrgencyLevel urgencyLevel;

    @Column(length = 1000)
    private String recommendations; // คำแนะนำเพิ่มเติม

    // ข้อมูลการวิเคราะห์
    @Column(nullable = false)
    private Integer analysisBasedOnDays; // วิเคราะห์จากข้อมูลกี่วันย้อนหลัง

    @Column(nullable = false)
    private LocalDateTime lastCalculatedDate; // วันที่คำนวณครั้งล่าสุด

    // Safety stock settings
    @Column(nullable = false)
    private Integer safetyStockDays; // ต้องการ safety stock กี่วัน (default: 7 วัน)

    @Column(nullable = false)
    private Integer leadTimeDays; // เวลาจัดส่งโดยประมาณ (default: 14 วัน)

    public enum UrgencyLevel {
        LOW("ไม่เร่งด่วน - Stock เพียงพอมากกว่า 30 วัน"),
        MEDIUM("ปานกลาง - Stock เหลือ 15-30 วัน"),
        HIGH("เร่งด่วน - Stock เหลือ 7-14 วัน"),
        CRITICAL("วิกฤต - Stock เหลือน้อยกว่า 7 วัน");

        private final String description;

        UrgencyLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @PrePersist
    @PreUpdate
    public void updateCalculatedDate() {
        this.lastCalculatedDate = LocalDateTime.now();
    }

    /**
     * คำนวณระดับความเร่งด่วนจากจำนวนวันที่เหลือ
     */
    public void calculateUrgencyLevel() {
        if (daysUntilStockOut <= 7) {
            this.urgencyLevel = UrgencyLevel.CRITICAL;
        } else if (daysUntilStockOut <= 14) {
            this.urgencyLevel = UrgencyLevel.HIGH;
        } else if (daysUntilStockOut <= 30) {
            this.urgencyLevel = UrgencyLevel.MEDIUM;
        } else {
            this.urgencyLevel = UrgencyLevel.LOW;
        }
    }

    /**
     * สร้างคำแนะนำอัตโนมัติ
     */
    public void generateRecommendations() {
        StringBuilder rec = new StringBuilder();

        rec.append(String.format("📊 การใช้งานเฉลี่ย: %d ชิ้น/วัน, %d ชิ้น/สัปดาห์\n",
                averageDailyUsage, averageWeeklyUsage));

        rec.append(String.format("⏰ คาดว่าจะหมด Stock ในอีก %d วัน (%s)\n",
                daysUntilStockOut, estimatedStockOutDate.toLocalDate()));

        if (urgencyLevel == UrgencyLevel.CRITICAL) {
            rec.append("🚨 แนะนำสั่งซื้อทันที! Stock เหลือน้อยมาก\n");
        } else if (urgencyLevel == UrgencyLevel.HIGH) {
            rec.append("⚠️ ควรเริ่มพิจารณาสั่งซื้อในสัปดาห์นี้\n");
        } else if (urgencyLevel == UrgencyLevel.MEDIUM) {
            rec.append("📋 วางแผนสั่งซื้อในอีก 1-2 สัปดาห์\n");
        } else {
            rec.append("✅ Stock ยังเพียงพอ ไม่ต้องรีบสั่งซื้อ\n");
        }

        rec.append(String.format("💰 ค่าใช้จ่ายโดยประมาณ: ฿%.2f\n", estimatedOrderCost));

        this.recommendations = rec.toString();
    }
}