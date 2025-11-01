package com.example.server.respository;

import com.example.server.entity.Order;
import com.example.server.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;


@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderOrderId(Long orderId);

    List<OrderItem> findByProductProductId(Long productId);

    List<OrderItem> findByStockDeductionStatus(OrderItem.StockDeductionStatus status);

    @Query("SELECT oi FROM OrderItem oi WHERE oi.order.orderId = :orderId")
    List<OrderItem> findItemsByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT SUM(oi.profit) FROM OrderItem oi WHERE oi.order.status NOT IN ('CANCELLED', 'RETURNED')")
    BigDecimal getTotalProfit();

    @Query("SELECT oi FROM OrderItem oi WHERE oi.productSku = :sku")
    List<OrderItem> findByProductSku(@Param("sku") String sku);

    void deleteByOrderOrderId(Long orderId);
}