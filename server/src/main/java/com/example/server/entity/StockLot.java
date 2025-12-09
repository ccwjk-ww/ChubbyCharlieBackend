package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "stock_lot")
@EqualsAndHashCode(exclude = {"items"})
@ToString(exclude = {"items"})
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockLotId;

    @Column(nullable = false, unique = true)
    private String lotName;

    private LocalDateTime importDate;
    private LocalDateTime arrivalDate;

    @Enumerated(EnumType.STRING)
    private StockStatus status = StockStatus.PENDING;

    // ⭐ แก้ไข: เปลี่ยนเป็น orphanRemoval = false และลบ cascade = CascadeType.ALL
    // เพื่อให้สามารถลบ Stock Item ได้โดยไม่กระทบ StockLot
    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = false)
    @JoinColumn(name = "stock_lot_id", nullable = true) // ⭐ เพิ่ม nullable = true
    private List<StockBase> items = new ArrayList<>();

    public enum StockStatus {
        PENDING,
        ARRIVED,
        COMPLETED,
        CANCELLED
    }
}