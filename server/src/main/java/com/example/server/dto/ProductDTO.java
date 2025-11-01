package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDTO {
    private Long productId;
    private String productName;
    private String description;
    private String sku;
    private String category;
    private BigDecimal sellingPrice;
    private BigDecimal calculatedCost;
    private BigDecimal profitMargin;
    private String status;

    /**
     * ⭐ เพิ่ม: URL ของรูปภาพสินค้า
     */
    private String imageUrl;

    /**
     * ⭐ เพิ่ม: flag บอกว่าใช้รูป default หรือไม่
     */
    private Boolean isUsingDefaultImage;

    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
    private List<ProductIngredientDTO> ingredients;
}