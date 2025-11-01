package com.example.server.respository;

import com.example.server.entity.EmployeeSalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeSalaryPaymentRepository extends JpaRepository<EmployeeSalaryPayment, Long> {

    // Find by employee
    List<EmployeeSalaryPayment> findByEmployeeEmpId(Long employeeId);

    // Find by payment month
    List<EmployeeSalaryPayment> findByPaymentMonth(YearMonth paymentMonth);

    // Find by employee and month
    Optional<EmployeeSalaryPayment> findByEmployeeEmpIdAndPaymentMonth(Long employeeId, YearMonth paymentMonth);

    // Find by status
    List<EmployeeSalaryPayment> findByStatus(EmployeeSalaryPayment.PaymentStatus status);

    // Find by type
    List<EmployeeSalaryPayment> findByType(EmployeeSalaryPayment.PaymentType type);

    // Find pending payments for a specific month
    @Query("SELECT sp FROM EmployeeSalaryPayment sp WHERE sp.paymentMonth = :month AND sp.status = 'PENDING'")
    List<EmployeeSalaryPayment> findPendingPaymentsByMonth(@Param("month") YearMonth month);

    // Calculate total paid for a month
    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM EmployeeSalaryPayment sp WHERE sp.paymentMonth = :month AND sp.status = 'PAID'")
    BigDecimal getTotalPaidForMonth(@Param("month") YearMonth month);

    // Calculate total paid for an employee
    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM EmployeeSalaryPayment sp WHERE sp.employee.empId = :employeeId AND sp.status = 'PAID'")
    BigDecimal getTotalPaidForEmployee(@Param("employeeId") Long employeeId);

    // Get payment history for employee
    @Query("SELECT sp FROM EmployeeSalaryPayment sp WHERE sp.employee.empId = :employeeId ORDER BY sp.paymentMonth DESC")
    List<EmployeeSalaryPayment> getPaymentHistoryForEmployee(@Param("employeeId") Long employeeId);

    // Check if payment exists for employee in month
    @Query("SELECT COUNT(sp) > 0 FROM EmployeeSalaryPayment sp WHERE sp.employee.empId = :employeeId AND sp.paymentMonth = :month")
    boolean existsByEmployeeAndMonth(@Param("employeeId") Long employeeId, @Param("month") YearMonth month);
}