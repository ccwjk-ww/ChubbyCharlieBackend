package com.example.server.respository;

import com.example.server.entity.ChinaStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface ChinaStockRepository extends JpaRepository<ChinaStock, Long> {

    List<ChinaStock> findByStatus(ChinaStock.StockStatus status);

    List<ChinaStock> findByStockLotId(Long stockLotId);

    @Query("SELECT c FROM ChinaStock c WHERE c.unitPriceYuan BETWEEN :minPrice AND :maxPrice")
    List<ChinaStock> findByUnitPriceYuanBetween(@Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    @Query("SELECT c FROM ChinaStock c WHERE c.quantity >= :minQuantity")
    List<ChinaStock> findByQuantityGreaterThanEqual(@Param("minQuantity") Integer minQuantity);

    @Query("SELECT SUM(c.totalBath) FROM ChinaStock c WHERE c.stockLotId = :stockLotId")
    BigDecimal getTotalValueByLot(@Param("stockLotId") Long stockLotId);

    @Query("SELECT c FROM ChinaStock c WHERE c.name LIKE %:keyword% OR c.shopURL LIKE %:keyword%")
    List<ChinaStock> searchByKeyword(@Param("keyword") String keyword);
}