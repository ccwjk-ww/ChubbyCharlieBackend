package com.example.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(name = "products")
@EqualsAndHashCode(exclude = {"productIngredients"})
@ToString(exclude = {"productIngredients"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(nullable = false)
    private String productName;

    private String description;
    private String sku;
    private String category;

    @Column(precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal calculatedCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal profitMargin;

    /**
     * ⭐ URL ของรูปภาพสินค้า
     */
    @Column(length = 500)
    private String imageUrl;

    /**
     * ⭐ Default Image - ใช้ logo.jpg
     */
    private static final String DEFAULT_IMAGE_URL = "http://localhost:8080/images/products/logo.jpg";

    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.ACTIVE;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, orphanRemoval = true)
    private List<ProductIngredient> productIngredients = new ArrayList<>();

    public enum ProductStatus {
        ACTIVE,
        INACTIVE,
        DISCONTINUED
    }

    @PrePersist
    public void onCreate() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();

        // ⭐ ถ้าไม่มีรูป ให้ใช้ default (logo.jpg)
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageUrl = DEFAULT_IMAGE_URL;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    /**
     * ⭐ Helper method: ดึง URL ของรูปภาพ
     */
    public String getImageUrl() {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return DEFAULT_IMAGE_URL;
        }
        return imageUrl;
    }

    /**
     * ⭐ Helper method: ตรวจสอบว่าใช้รูป default หรือไม่
     */
    public boolean isUsingDefaultImage() {
        return imageUrl == null ||
                imageUrl.trim().isEmpty() ||
                imageUrl.equals(DEFAULT_IMAGE_URL) ||
                imageUrl.endsWith("/images/products/logo.jpg");
    }
}