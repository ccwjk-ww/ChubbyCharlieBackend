package com.example.server.respository;

import com.example.server.entity.StockForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockForecastRepository extends JpaRepository<StockForecast, Long> {

    /**
     * หา Forecast ล่าสุดของ Stock Item
     */
    Optional<StockForecast> findTopByStockItemStockItemIdOrderByLastCalculatedDateDesc(Long stockItemId);

    /**
     * หา Forecast ทั้งหมดตามระดับความเร่งด่วน
     */
    List<StockForecast> findByUrgencyLevelOrderByDaysUntilStockOutAsc(StockForecast.UrgencyLevel urgencyLevel);

    /**
     * หา Stock ที่ต้องสั่งซื้อเร่งด่วน (CRITICAL และ HIGH)
     */
    @Query("SELECT sf FROM StockForecast sf WHERE sf.urgencyLevel IN ('CRITICAL', 'HIGH') " +
            "ORDER BY sf.daysUntilStockOut ASC, sf.urgencyLevel DESC")
    List<StockForecast> findUrgentStockItems();

    /**
     * หา Stock ที่จะหมดในจำนวนวันที่กำหนด
     */
    @Query("SELECT sf FROM StockForecast sf WHERE sf.daysUntilStockOut <= :days " +
            "ORDER BY sf.daysUntilStockOut ASC")
    List<StockForecast> findStockRunningOutInDays(@Param("days") int days);

    /**
     * หา Forecast ที่คำนวณล่าสุดในช่วงเวลาที่กำหนด
     */
    @Query("SELECT sf FROM StockForecast sf WHERE sf.lastCalculatedDate >= :since " +
            "ORDER BY sf.lastCalculatedDate DESC")
    List<StockForecast> findRecentForecasts(@Param("since") LocalDateTime since);

    /**
     * หา Stock ที่มี usage สูงที่สุด
     */
    @Query("SELECT sf FROM StockForecast sf ORDER BY sf.averageDailyUsage DESC")
    List<StockForecast> findHighUsageItems();

    /**
     * หา Forecast ตาม Stock Type
     */
    List<StockForecast> findByStockTypeOrderByUrgencyLevelDescDaysUntilStockOutAsc(String stockType);

    /**
     * นับจำนวน Stock ในแต่ละระดับความเร่งด่วน
     */
    @Query("SELECT sf.urgencyLevel, COUNT(sf) FROM StockForecast sf GROUP BY sf.urgencyLevel")
    List<Object[]> countByUrgencyLevel();

    /**
     * คำนวณต้นทุนรวมที่ต้องสั่งซื้อตามระดับความเร่งด่วน
     */
    @Query("SELECT SUM(sf.estimatedOrderCost) FROM StockForecast sf WHERE sf.urgencyLevel = :urgencyLevel")
    Double getTotalEstimatedCostByUrgencyLevel(@Param("urgencyLevel") StockForecast.UrgencyLevel urgencyLevel);

    /**
     * หา Stock ที่ต้องสั่งซื้อในสัปดาห์นี้
     */
    @Query("SELECT sf FROM StockForecast sf WHERE sf.daysUntilStockOut <= (sf.leadTimeDays + sf.safetyStockDays) " +
            "ORDER BY sf.urgencyLevel DESC, sf.daysUntilStockOut ASC")
    List<StockForecast> findStockToOrderThisWeek();

    /**
     * ลบ Forecast เก่าที่เกินกำหนด
     */
    @Query("DELETE FROM StockForecast sf WHERE sf.lastCalculatedDate < :cutoffDate")
    void deleteOldForecasts(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * หา Top N Stock ที่ใช้งานมากที่สุด
     */
    @Query("SELECT sf FROM StockForecast sf ORDER BY sf.averageMonthlyUsage DESC")
    List<StockForecast> findTopUsageItems();
}