package com.example.server.respository;

import com.example.server.entity.ThaiStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface ThaiStockRepository extends JpaRepository<ThaiStock, Long> {

    List<ThaiStock> findByStatus(ThaiStock.StockStatus status);

    List<ThaiStock> findByStockLotId(Long stockLotId);

    @Query("SELECT t FROM ThaiStock t WHERE t.priceTotal BETWEEN :minPrice AND :maxPrice")
    List<ThaiStock> findByPriceTotalBetween(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT t FROM ThaiStock t WHERE t.quantity >= :minQuantity")
    List<ThaiStock> findByQuantityGreaterThanEqual(@Param("minQuantity") Integer minQuantity);

    @Query("SELECT SUM(t.priceTotal + t.shippingCost) FROM ThaiStock t WHERE t.stockLotId = :stockLotId")
    BigDecimal getTotalValueByLot(@Param("stockLotId") Long stockLotId);

    @Query("SELECT t FROM ThaiStock t WHERE t.name LIKE %:keyword% OR t.shopURL LIKE %:keyword%")
    List<ThaiStock> searchByKeyword(@Param("keyword") String keyword);
}