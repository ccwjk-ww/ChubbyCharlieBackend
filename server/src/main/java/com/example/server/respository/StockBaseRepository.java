// StockBaseRepository.java
package com.example.server.respository;

import com.example.server.entity.StockBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface StockBaseRepository extends JpaRepository<StockBase, Long> {

    List<StockBase> findByStatus(StockBase.StockStatus status);
    List<StockBase> findByStockLotId(Long stockLotId);

    @Query("SELECT s FROM StockBase s WHERE s.name LIKE %:name%")
    List<StockBase> findByNameContaining(@Param("name") String name);

    @Query("SELECT s FROM StockBase s WHERE s.shopURL LIKE %:url%")
    List<StockBase> findByShopURLContaining(@Param("url") String url);

    // ✅ ใช้ PESSIMISTIC_WRITE กันแข่งกันตัดสต็อก
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StockBase s WHERE s.stockItemId = :id")
    Optional<StockBase> lockById(@Param("id") Long id);
}
