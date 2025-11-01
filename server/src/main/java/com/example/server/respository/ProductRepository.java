package com.example.server.respository;

import com.example.server.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductName(String productName);

    Optional<Product> findBySku(String sku);

    List<Product> findByStatus(Product.ProductStatus status);

    List<Product> findByCategory(String category);

    @Query("SELECT p FROM Product p WHERE p.productName LIKE %:keyword% OR p.sku LIKE %:keyword% OR p.description LIKE %:keyword%")
    List<Product> searchProducts(@Param("keyword") String keyword);

    @Query("SELECT p FROM Product p WHERE p.sellingPrice BETWEEN :minPrice AND :maxPrice")
    List<Product> findBySellingPriceBetween(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT p FROM Product p WHERE p.calculatedCost BETWEEN :minCost AND :maxCost")
    List<Product> findByCalculatedCostBetween(@Param("minCost") BigDecimal minCost, @Param("maxCost") BigDecimal maxCost);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.category IS NOT NULL ORDER BY p.category")
    List<String> findAllCategories();
}