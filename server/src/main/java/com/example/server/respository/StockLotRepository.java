package com.example.server.respository;

import com.example.server.entity.StockLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StockLotRepository extends JpaRepository<StockLot, Long> {

    Optional<StockLot> findByLotName(String lotName);

    List<StockLot> findByStatus(StockLot.StockStatus status);

    List<StockLot> findByImportDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    List<StockLot> findByArrivalDateBetween(LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT s FROM StockLot s WHERE s.lotName LIKE %:keyword% OR s.status = :status")
    List<StockLot> findByLotNameContainingOrStatus(@Param("keyword") String keyword, @Param("status") StockLot.StockStatus status);
}