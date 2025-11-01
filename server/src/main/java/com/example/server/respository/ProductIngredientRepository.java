package com.example.server.respository;

import com.example.server.entity.Product;
import com.example.server.entity.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {

    List<ProductIngredient> findByProductProductId(Long productId);

    List<ProductIngredient> findByStockItemStockItemId(Long stockItemId);

    @Query("SELECT pi FROM ProductIngredient pi WHERE pi.ingredientName LIKE %:name%")
    List<ProductIngredient> findByIngredientNameContaining(@Param("name") String name);

    // หา Products ที่ใช้ Stock Item ตัวใดตัวหนึ่ง
    @Query("SELECT DISTINCT pi.product FROM ProductIngredient pi WHERE pi.stockItem.stockItemId = :stockItemId")
    List<Product> findProductsUsingStockItem(@Param("stockItemId") Long stockItemId);

    // ✅ เพิ่ม: ลบ ingredients ทั้งหมดของ product
    @Modifying
    @Query("DELETE FROM ProductIngredient pi WHERE pi.product.productId = :productId")
    void deleteByProductId(@Param("productId") Long productId);
}