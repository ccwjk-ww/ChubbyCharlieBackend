// ProductIngredientStockAllocationRepository.java
package com.example.server.respository;

import com.example.server.entity.ProductIngredientStockAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductIngredientStockAllocationRepository extends JpaRepository<ProductIngredientStockAllocation, Long> {

    List<ProductIngredientStockAllocation> findByProductIngredientIngredientId(Long ingredientId);

    List<ProductIngredientStockAllocation> findByStockItemStockItemId(Long stockItemId);

    @Query("SELECT a FROM ProductIngredientStockAllocation a WHERE a.productIngredient.ingredientId = :ingredientId ORDER BY a.allocationPriority ASC")
    List<ProductIngredientStockAllocation> findByIngredientIdOrderByPriority(@Param("ingredientId") Long ingredientId);

    @Query("SELECT DISTINCT a.productIngredient.product FROM ProductIngredientStockAllocation a WHERE a.stockItem.stockItemId = :stockItemId")
    List<com.example.server.entity.Product> findProductsUsingStockItemInAllocations(@Param("stockItemId") Long stockItemId);
}