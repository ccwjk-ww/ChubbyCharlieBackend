package com.example.server.respository;

import com.example.server.entity.DefectiveRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DefectiveRecordRepository extends JpaRepository<DefectiveRecord, Long> {

    List<DefectiveRecord> findByStockItemIdOrderByRecordedAtDesc(Long stockItemId);

    @Query("SELECT SUM(d.count) FROM DefectiveRecord d WHERE d.stockItemId = :stockItemId")
    Integer sumCountByStockItemId(@Param("stockItemId") Long stockItemId);

    @Query("SELECT SUM(d.totalValue) FROM DefectiveRecord d WHERE d.stockItemId = :stockItemId")
    java.math.BigDecimal sumTotalValueByStockItemId(@Param("stockItemId") Long stockItemId);

    void deleteByStockItemId(Long stockItemId);
}