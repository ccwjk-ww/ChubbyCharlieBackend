package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "defective_record")
public class DefectiveRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @Column(name = "stock_item_id", nullable = false)
    private Long stockItemId;

    @Column(nullable = false)
    private Integer count;

    @Column(precision = 10, scale = 3)
    private BigDecimal unitCost;

    @Column(precision = 10, scale = 3)
    private BigDecimal totalValue;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Column(length = 500)
    private String note;

    @Column(name = "stock_type", length = 10)
    private String stockType; // "CHINA" or "THAI"
}