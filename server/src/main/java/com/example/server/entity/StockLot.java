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
@EqualsAndHashCode(exclude = {"items"}) // ป้องกัน infinite loop
@ToString(exclude = {"items"}) // ป้องกัน infinite loop ใน toString
public class StockLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stockLotId;

    @Column(nullable = false, unique = true)
    private String lotName;

    private LocalDateTime importDate;
    private LocalDateTime arrivalDate;

    // ลบ totalShippingBath field ออกแล้ว

    @Enumerated(EnumType.STRING)
    private StockStatus status = StockStatus.PENDING;

    // เปลี่ยนเป็น EAGER loading และใช้ @JoinColumn
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "stock_lot_id") // ใช้ foreign key แทน mappedBy
    private List<StockBase> items = new ArrayList<>();

    public enum StockStatus {
        PENDING,
        ARRIVED,
        COMPLETED,
        CANCELLED
    }
}