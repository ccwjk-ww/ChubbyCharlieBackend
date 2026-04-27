package com.example.server.respository;

import com.example.server.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    @Query("SELECT c FROM Customer c WHERE c.customerName LIKE %:keyword% OR c.customerPhone LIKE %:keyword%")
    List<Customer> findByCustomerNameOrPhoneContaining(@Param("keyword") String keyword);
    // ⭐ ค้นหาตาม Status
    List<Customer> findByStatus(Customer.Status status);

    // ⭐ ค้นหาด้วย keyword และ status
    @Query("SELECT c FROM Customer c WHERE (c.customerName LIKE %:keyword% OR c.customerPhone LIKE %:keyword%) AND c.status = :status")
    List<Customer> findByKeywordAndStatus(@Param("keyword") String keyword, @Param("status") Customer.Status status);

    // ⭐ นับจำนวนตาม status
    long countByStatus(Customer.Status status);
}