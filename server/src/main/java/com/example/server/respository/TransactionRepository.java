package com.example.server.respository;

import com.example.server.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Find by type
    List<Transaction> findByType(Transaction.TransactionType type);

    // Find by category
    List<Transaction> findByCategory(Transaction.TransactionCategory category);

    // Find by date range
    @Query("SELECT t FROM Transaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate ORDER BY t.transactionDate DESC")
    List<Transaction> findByDateRange(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    // Find by mode
    List<Transaction> findByMode(Transaction.TransactionMode mode);

    // Find by order ID
    List<Transaction> findByOrderId(Long orderId);

    // Find by stock lot ID
    List<Transaction> findByStockLotId(Long stockLotId);

    // Find by employee ID
    List<Transaction> findByEmployeeId(Long employeeId);

    // Calculate total income
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'INCOME'")
    BigDecimal getTotalIncome();

    // Calculate total expense
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'EXPENSE'")
    BigDecimal getTotalExpense();

    // Calculate total income by date range
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'INCOME' AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalIncomeByDateRange(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    // Calculate total expense by date range
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.type = 'EXPENSE' AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpenseByDateRange(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    // Calculate net profit (income - expense)
    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) FROM Transaction t")
    BigDecimal getNetProfit();

    // Calculate net profit by date range
    @Query("SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END), 0) FROM Transaction t WHERE t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal getNetProfitByDateRange(@Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    // Get recent transactions
    @Query("SELECT t FROM Transaction t ORDER BY t.transactionDate DESC")
    List<Transaction> findAllOrderByDateDesc();

    // Search transactions
    @Query("SELECT t FROM Transaction t WHERE t.description LIKE %:keyword% OR t.notes LIKE %:keyword% ORDER BY t.transactionDate DESC")
    List<Transaction> searchTransactions(@Param("keyword") String keyword);

    // Count by category
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.category = :category")
    Long countByCategory(@Param("category") Transaction.TransactionCategory category);
}