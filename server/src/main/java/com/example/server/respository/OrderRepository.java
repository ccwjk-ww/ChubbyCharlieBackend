//package com.example.server.respository;
//
//import com.example.server.entity.Order;
//import com.example.server.entity.OrderItem;
//import org.springframework.data.jpa.repository.JpaRepository;
//import org.springframework.data.jpa.repository.Query;
//import org.springframework.data.repository.query.Param;
//import org.springframework.stereotype.Repository;
//import java.time.LocalDateTime;
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Optional;
//
//@Repository
//public interface OrderRepository extends JpaRepository<Order, Long> {
//
//    Optional<Order> findByOrderNumber(String orderNumber);
//
//    List<Order> findByStatus(Order.OrderStatus status);
//
//    List<Order> findBySource(Order.OrderSource source);
//
//    List<Order> findByPaymentStatus(Order.PaymentStatus paymentStatus);
//
//    List<Order> findByCustomerCustomerId(Long customerId);
//
//    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :startDate AND :endDate")
//    List<Order> findByOrderDateBetween(@Param("startDate") LocalDateTime startDate,
//                                       @Param("endDate") LocalDateTime endDate);
//
//    @Query("SELECT o FROM Order o WHERE o.orderNumber LIKE %:keyword% OR o.customerName LIKE %:keyword% OR o.customerPhone LIKE %:keyword%")
//    List<Order> searchOrders(@Param("keyword") String keyword);
//
//    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
//    Long countByStatus(@Param("status") Order.OrderStatus status);
//
//    @Query("SELECT SUM(o.netAmount) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED')")
//    BigDecimal getTotalRevenue();
//
//    @Query("SELECT COUNT(o) FROM Order o WHERE o.source = :source")
//    Long countBySource(@Param("source") Order.OrderSource source);
//
//    @Query("SELECT o FROM Order o ORDER BY o.orderDate DESC")
//    List<Order> findAllOrderByOrderDateDesc();
//}
package com.example.server.respository;

import com.example.server.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByStatus(Order.OrderStatus status);

    List<Order> findBySource(Order.OrderSource source);

    List<Order> findByPaymentStatus(Order.PaymentStatus paymentStatus);

    List<Order> findByCustomerCustomerId(Long customerId);

    // ============================================
    // ✅ NEW: Queries with JOIN FETCH
    // ============================================

    /**
     * ✅ ดึง Order พร้อม Items ในคำสั่งเดียว - แก้ N+1 problem
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems ORDER BY o.orderDate DESC")
    List<Order> findAllOrdersWithItems();

    /**
     * ✅ ดึง Order ตาม ID พร้อม Items
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.orderId = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    /**
     * ✅ ดึง Order ตาม OrderNumber พร้อม Items
     */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    /**
     * ✅ ดึง Orders ตาม Status พร้อม Items
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.status = :status ORDER BY o.orderDate DESC")
    List<Order> findByStatusWithItems(@Param("status") Order.OrderStatus status);

    /**
     * ✅ ดึง Orders ตาม Source พร้อม Items
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.source = :source ORDER BY o.orderDate DESC")
    List<Order> findBySourceWithItems(@Param("source") Order.OrderSource source);

    /**
     * ✅ Search Orders พร้อม Items
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems " +
            "WHERE o.orderNumber LIKE %:keyword% " +
            "OR o.customerName LIKE %:keyword% " +
            "OR o.customerPhone LIKE %:keyword% " +
            "ORDER BY o.orderDate DESC")
    List<Order> searchOrdersWithItems(@Param("keyword") String keyword);

    // ============================================
    // Original Queries (Keep for compatibility)
    // ============================================

    @Query("SELECT o FROM Order o WHERE o.orderDate BETWEEN :startDate AND :endDate ORDER BY o.orderDate DESC")
    List<Order> findByOrderDateBetween(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query("SELECT o FROM Order o WHERE o.orderNumber LIKE %:keyword% " +
            "OR o.customerName LIKE %:keyword% " +
            "OR o.customerPhone LIKE %:keyword% " +
            "ORDER BY o.orderDate DESC")
    List<Order> searchOrders(@Param("keyword") String keyword);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Long countByStatus(@Param("status") Order.OrderStatus status);

    @Query("SELECT SUM(o.netAmount) FROM Order o WHERE o.status NOT IN ('CANCELLED', 'RETURNED')")
    BigDecimal getTotalRevenue();

    @Query("SELECT COUNT(o) FROM Order o WHERE o.source = :source")
    Long countBySource(@Param("source") Order.OrderSource source);

    @Query("SELECT o FROM Order o ORDER BY o.orderDate DESC")
    List<Order> findAllOrderByOrderDateDesc();
}