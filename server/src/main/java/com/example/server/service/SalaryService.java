//package com.example.server.service;
//
//import com.example.server.entity.Employee;
//import com.example.server.entity.EmployeeSalaryPayment;
//import com.example.server.entity.Transaction;
//import com.example.server.respository.EmployeeRepository;
//import com.example.server.respository.EmployeeSalaryPaymentRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.time.YearMonth;
//import java.util.*;
//
//@Service
//@Transactional
//public class SalaryService {
//
//    @Autowired
//    private EmployeeSalaryPaymentRepository salaryPaymentRepository;
//
//    @Autowired
//    private EmployeeRepository employeeRepository;
//
//    @Autowired
//    private TransactionService transactionService;
//
//    // ============================================
//    // CRUD Operations
//    // ============================================
//
//    @Transactional(readOnly = true)
//    public List<EmployeeSalaryPayment> getAllPayments() {
//        return salaryPaymentRepository.findAll();
//    }
//
//    @Transactional(readOnly = true)
//    public Optional<EmployeeSalaryPayment> getPaymentById(Long id) {
//        return salaryPaymentRepository.findById(id);
//    }
//
//    @Transactional(readOnly = true)
//    public List<EmployeeSalaryPayment> getPaymentsByEmployee(Long employeeId) {
//        return salaryPaymentRepository.findByEmployeeEmpId(employeeId);
//    }
//
//    @Transactional(readOnly = true)
//    public List<EmployeeSalaryPayment> getPaymentsByMonth(YearMonth month) {
//        return salaryPaymentRepository.findByPaymentMonth(month);
//    }
//
//    @Transactional(readOnly = true)
//    public List<EmployeeSalaryPayment> getPendingPayments(YearMonth month) {
//        return salaryPaymentRepository.findPendingPaymentsByMonth(month);
//    }
//
//    // ============================================
//    // Create Salary Payment
//    // ============================================
//
//    /**
//     * สร้าง Salary Payment แบบ Manual
//     */
//    public EmployeeSalaryPayment createSalaryPayment(EmployeeSalaryPayment payment) {
//        validateSalaryPayment(payment);
//
//        // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
//        boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
//                payment.getEmployee().getEmpId(),
//                payment.getPaymentMonth()
//        );
//
//        if (exists) {
//            throw new IllegalStateException("Salary already paid for this employee in this month");
//        }
//
//        // Save payment
//        EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);
//
//        // สร้าง Transaction ถ้าสถานะเป็น PAID
//        if (savedPayment.getStatus() == EmployeeSalaryPayment.PaymentStatus.PAID) {
//            Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
//            savedPayment.setTransactionId(transaction.getTransactionId());
//            savedPayment = salaryPaymentRepository.save(savedPayment);
//        }
//
//        return savedPayment;
//    }
//
//    /**
//     * ✅ จ่ายเงินเดือนหมู่สำหรับเดือนนั้นๆ
//     */
//    public Map<String, Object> processMonthlyPayments(YearMonth paymentMonth,
//                                                      LocalDateTime paymentDate,
//                                                      Map<Long, Integer> dailyEmployeeWorkDays) {
//        Map<String, Object> result = new HashMap<>();
//        List<String> successMessages = new ArrayList<>();
//        List<String> errorMessages = new ArrayList<>();
//
//        int successCount = 0;
//        int errorCount = 0;
//        BigDecimal totalPaid = BigDecimal.ZERO;
//
//        // 1. จ่ายเงินเดือนพนักงานรายเดือน
//        List<Employee> monthlyEmployees = employeeRepository.findAll().stream()
//                .filter(e -> "MONTHLY".equalsIgnoreCase(e.getEmpType()) &&
//                        e.getStatus() == Employee.Status.ACTIVE)
//                .toList();
//
//        for (Employee employee : monthlyEmployees) {
//            try {
//                // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
//                boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
//                        employee.getEmpId(), paymentMonth);
//
//                if (exists) {
//                    errorMessages.add(String.format("❌ %s - จ่ายไปแล้ว", employee.getEmpName()));
//                    errorCount++;
//                    continue;
//                }
//
//                EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
//                payment.setEmployee(employee);
//                payment.setPaymentMonth(paymentMonth);
//                payment.setAmount(employee.getMonthlySalary());
//                payment.setPaymentDate(paymentDate);
//                payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
//                payment.setType(EmployeeSalaryPayment.PaymentType.MONTHLY);
//
//                EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);
//
//                // สร้าง Transaction
//                Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
//                savedPayment.setTransactionId(transaction.getTransactionId());
//                salaryPaymentRepository.save(savedPayment);
//
//                totalPaid = totalPaid.add(employee.getMonthlySalary());
//                successCount++;
//                successMessages.add(String.format("✅ %s - %,.2f บาท (รายเดือน)",
//                        employee.getEmpName(), employee.getMonthlySalary()));
//
//            } catch (Exception e) {
//                errorMessages.add(String.format("❌ %s - Error: %s",
//                        employee.getEmpName(), e.getMessage()));
//                errorCount++;
//            }
//        }
//
//        // 2. จ่ายเงินเดือนพนักงานรายวัน
//        List<Employee> dailyEmployees = employeeRepository.findAll().stream()
//                .filter(e -> "DAILY".equalsIgnoreCase(e.getEmpType()) &&
//                        e.getStatus() == Employee.Status.ACTIVE)
//                .toList();
//
//        for (Employee employee : dailyEmployees) {
//            try {
//                // ต้องมีข้อมูลจำนวนวันทำงาน
//                if (!dailyEmployeeWorkDays.containsKey(employee.getEmpId())) {
//                    errorMessages.add(String.format("❌ %s - ไม่มีข้อมูลจำนวนวันทำงาน",
//                            employee.getEmpName()));
//                    errorCount++;
//                    continue;
//                }
//
//                Integer workDays = dailyEmployeeWorkDays.get(employee.getEmpId());
//
//                // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
//                boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
//                        employee.getEmpId(), paymentMonth);
//
//                if (exists) {
//                    errorMessages.add(String.format("❌ %s - จ่ายไปแล้ว", employee.getEmpName()));
//                    errorCount++;
//                    continue;
//                }
//
//                BigDecimal totalSalary = employee.getDailyWage().multiply(BigDecimal.valueOf(workDays));
//
//                EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
//                payment.setEmployee(employee);
//                payment.setPaymentMonth(paymentMonth);
//                payment.setAmount(totalSalary);
//                payment.setPaymentDate(paymentDate);
//                payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
//                payment.setType(EmployeeSalaryPayment.PaymentType.DAILY);
//                payment.setWorkDays(workDays);
//
//                EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);
//
//                // สร้าง Transaction
//                Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
//                savedPayment.setTransactionId(transaction.getTransactionId());
//                salaryPaymentRepository.save(savedPayment);
//
//                totalPaid = totalPaid.add(totalSalary);
//                successCount++;
//                successMessages.add(String.format("✅ %s - %,.2f บาท (รายวัน %d วัน)",
//                        employee.getEmpName(), totalSalary.doubleValue(), workDays));
//
//            } catch (Exception e) {
//                errorMessages.add(String.format("❌ %s - Error: %s",
//                        employee.getEmpName(), e.getMessage()));
//                errorCount++;
//            }
//        }
//
//        result.put("success", errorCount == 0);
//        result.put("successCount", successCount);
//        result.put("errorCount", errorCount);
//        result.put("totalPaid", totalPaid);
//        result.put("successMessages", successMessages);
//        result.put("errorMessages", errorMessages);
//
//        return result;
//    }
//
//    /**
//     * ✅ อัพเดทสถานะการจ่ายเงิน
//     */
//    public EmployeeSalaryPayment updatePaymentStatus(Long paymentId,
//                                                     EmployeeSalaryPayment.PaymentStatus newStatus) {
//        EmployeeSalaryPayment payment = salaryPaymentRepository.findById(paymentId)
//                .orElseThrow(() -> new RuntimeException("Payment not found"));
//
//        EmployeeSalaryPayment.PaymentStatus oldStatus = payment.getStatus();
//        payment.setStatus(newStatus);
//
//        EmployeeSalaryPayment updatedPayment = salaryPaymentRepository.save(payment);
//
//        // ถ้าเปลี่ยนจาก PENDING -> PAID ให้สร้าง Transaction
//        if (oldStatus == EmployeeSalaryPayment.PaymentStatus.PENDING &&
//                newStatus == EmployeeSalaryPayment.PaymentStatus.PAID) {
//
//            Transaction transaction = transactionService.createSalaryPaymentTransaction(updatedPayment);
//            updatedPayment.setTransactionId(transaction.getTransactionId());
//            updatedPayment = salaryPaymentRepository.save(updatedPayment);
//        }
//
//        return updatedPayment;
//    }
//
//    // ============================================
//    // ⭐ AUTO SCHEDULER - รันทุกวันที่ 25 เวลา 00:00
//    // ============================================
//
//    /**
//     * ✅ Schedule: รันทุกวันที่ 25 ของทุกเดือน เวลา 00:00:00
//     * cron = "0 0 0 25 * ?"
//     * หมายถึง: วินาที 0, นาที 0, ชั่วโมง 0, วันที่ 25, ทุกเดือน, ทุกวัน
//     */
//    @Scheduled(cron = "0 0 0 25 * ?") // รันทุกวันที่ 25 เวลาเที่ยงคืน
//    public void autoProcessMonthlySalaries() {
//        System.out.println("🔄 [AUTO] Starting monthly salary processing...");
//
//        YearMonth currentMonth = YearMonth.now();
//        LocalDateTime paymentDate = LocalDateTime.now();
//
//        try {
//            // 1. ดึงพนักงานรายเดือน ACTIVE ทั้งหมด
//            List<Employee> monthlyEmployees = employeeRepository.findAll().stream()
//                    .filter(e -> "MONTHLY".equalsIgnoreCase(e.getEmpType()) &&
//                            e.getStatus() == Employee.Status.ACTIVE)
//                    .toList();
//
//            int successCount = 0;
//            int skipCount = 0;
//
//            for (Employee employee : monthlyEmployees) {
//                try {
//                    // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
//                    boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
//                            employee.getEmpId(), currentMonth);
//
//                    if (exists) {
//                        System.out.println(String.format("⏭️ Skip: %s - Already paid",
//                                employee.getEmpName()));
//                        skipCount++;
//                        continue;
//                    }
//
//                    EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
//                    payment.setEmployee(employee);
//                    payment.setPaymentMonth(currentMonth);
//                    payment.setAmount(employee.getMonthlySalary());
//                    payment.setPaymentDate(paymentDate);
//                    payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
//                    payment.setType(EmployeeSalaryPayment.PaymentType.MONTHLY);
//                    payment.setNotes("Auto-processed on day 25");
//
//                    EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);
//
//                    // สร้าง Transaction
//                    Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
//                    savedPayment.setTransactionId(transaction.getTransactionId());
//                    salaryPaymentRepository.save(savedPayment);
//
//                    System.out.println(String.format("✅ Paid: %s - %,.2f THB",
//                            employee.getEmpName(), employee.getMonthlySalary()));
//                    successCount++;
//
//                } catch (Exception e) {
//                    System.err.println(String.format("❌ Error processing %s: %s",
//                            employee.getEmpName(), e.getMessage()));
//                }
//            }
//
//            System.out.println(String.format(
//                    "✅ [AUTO] Salary processing completed. Success: %d, Skipped: %d, Total: %d",
//                    successCount, skipCount, monthlyEmployees.size()));
//
//        } catch (Exception e) {
//            System.err.println("❌ [AUTO] Salary processing failed: " + e.getMessage());
//            e.printStackTrace();
//        }
//    }
//
//    // ============================================
//    // Summary
//    // ============================================
//
//    @Transactional(readOnly = true)
//    public Map<String, Object> getSalarySummary(YearMonth month) {
//        Map<String, Object> summary = new HashMap<>();
//
//        BigDecimal totalPaid = salaryPaymentRepository.getTotalPaidForMonth(month);
//        List<EmployeeSalaryPayment> payments = salaryPaymentRepository.findByPaymentMonth(month);
//
//        long monthlyCount = payments.stream()
//                .filter(p -> p.getType() == EmployeeSalaryPayment.PaymentType.MONTHLY)
//                .count();
//
//        long dailyCount = payments.stream()
//                .filter(p -> p.getType() == EmployeeSalaryPayment.PaymentType.DAILY)
//                .count();
//
//        summary.put("month", month);
//        summary.put("totalPaid", totalPaid);
//        summary.put("totalPayments", payments.size());
//        summary.put("monthlyEmployeeCount", monthlyCount);
//        summary.put("dailyEmployeeCount", dailyCount);
//
//        return summary;
//    }
//
//    // ============================================
//    // Helper Methods
//    // ============================================
//
//    private void validateSalaryPayment(EmployeeSalaryPayment payment) {
//        if (payment.getEmployee() == null) {
//            throw new IllegalArgumentException("Employee is required");
//        }
//        if (payment.getPaymentMonth() == null) {
//            throw new IllegalArgumentException("Payment month is required");
//        }
//        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
//            throw new IllegalArgumentException("Amount must be greater than zero");
//        }
//    }
//}
package com.example.server.service;

import com.example.server.entity.Employee;
import com.example.server.entity.EmployeeSalaryPayment;
import com.example.server.entity.Transaction;
import com.example.server.respository.EmployeeRepository;
import com.example.server.respository.EmployeeSalaryPaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
@Transactional
public class SalaryService {

    @Autowired
    private EmployeeSalaryPaymentRepository salaryPaymentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TransactionService transactionService;

    // ============================================
    // CRUD Operations
    // ============================================

    @Transactional(readOnly = true)
    public List<EmployeeSalaryPayment> getAllPayments() {
        return salaryPaymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeSalaryPayment> getPaymentById(Long id) {
        return salaryPaymentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSalaryPayment> getPaymentsByEmployee(Long employeeId) {
        return salaryPaymentRepository.findByEmployeeEmpId(employeeId);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSalaryPayment> getPaymentsByMonth(YearMonth month) {
        return salaryPaymentRepository.findByPaymentMonth(month);
    }

    @Transactional(readOnly = true)
    public List<EmployeeSalaryPayment> getPendingPayments(YearMonth month) {
        return salaryPaymentRepository.findPendingPaymentsByMonth(month);
    }

    // ============================================
    // Create Salary Payment
    // ============================================

    /**
     * ✅ สร้าง Salary Payment แบบ Manual
     */
    public EmployeeSalaryPayment createSalaryPayment(EmployeeSalaryPayment payment) {
        validateSalaryPayment(payment);

        // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
        boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
                payment.getEmployee().getEmpId(),
                payment.getPaymentMonth()
        );

        if (exists) {
            throw new IllegalStateException("Salary already paid for this employee in this month");
        }

        // Save payment
        EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);

        // สร้าง Transaction ถ้าสถานะเป็น PAID
        if (savedPayment.getStatus() == EmployeeSalaryPayment.PaymentStatus.PAID) {
            Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
            savedPayment.setTransactionId(transaction.getTransactionId());
            savedPayment = salaryPaymentRepository.save(savedPayment);
        }

        return savedPayment;
    }

    /**
     * ✅ จ่ายเงินเดือนหมู่สำหรับเดือนนั้นๆ
     */
    public Map<String, Object> processMonthlyPayments(YearMonth paymentMonth,
                                                      LocalDateTime paymentDate,
                                                      Map<Long, Integer> dailyEmployeeWorkDays) {
        Map<String, Object> result = new HashMap<>();
        List<String> successMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();

        int successCount = 0;
        int errorCount = 0;
        BigDecimal totalPaid = BigDecimal.ZERO;

        // 1. จ่ายเงินเดือนพนักงานรายเดือน
        List<Employee> monthlyEmployees = employeeRepository.findAll().stream()
                .filter(e -> "MONTHLY".equalsIgnoreCase(e.getEmpType()) &&
                        e.getStatus() == Employee.Status.ACTIVE)
                .toList();

        for (Employee employee : monthlyEmployees) {
            try {
                // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
                boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
                        employee.getEmpId(), paymentMonth);

                if (exists) {
                    errorMessages.add(String.format("❌ %s - จ่ายไปแล้ว", employee.getEmpName()));
                    errorCount++;
                    continue;
                }

                BigDecimal salaryAmount = BigDecimal.valueOf(employee.getMonthlySalary());

                EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
                payment.setEmployee(employee);
                payment.setPaymentMonth(paymentMonth);
                payment.setAmount(salaryAmount);
                payment.setPaymentDate(paymentDate);
                payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
                payment.setType(EmployeeSalaryPayment.PaymentType.MONTHLY);

                EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);

                // สร้าง Transaction
                Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
                savedPayment.setTransactionId(transaction.getTransactionId());
                salaryPaymentRepository.save(savedPayment);

                totalPaid = totalPaid.add(salaryAmount);
                successCount++;
                successMessages.add(String.format("✅ %s - %,.2f บาท (รายเดือน)",
                        employee.getEmpName(), salaryAmount.doubleValue()));

            } catch (Exception e) {
                errorMessages.add(String.format("❌ %s - Error: %s",
                        employee.getEmpName(), e.getMessage()));
                errorCount++;
            }
        }

        // 2. จ่ายเงินเดือนพนักงานรายวัน
        List<Employee> dailyEmployees = employeeRepository.findAll().stream()
                .filter(e -> "DAILY".equalsIgnoreCase(e.getEmpType()) &&
                        e.getStatus() == Employee.Status.ACTIVE)
                .toList();

        for (Employee employee : dailyEmployees) {
            try {
                // ต้องมีข้อมูลจำนวนวันทำงาน
                if (!dailyEmployeeWorkDays.containsKey(employee.getEmpId())) {
                    errorMessages.add(String.format("❌ %s - ไม่มีข้อมูลจำนวนวันทำงาน",
                            employee.getEmpName()));
                    errorCount++;
                    continue;
                }

                Integer workDays = dailyEmployeeWorkDays.get(employee.getEmpId());

                // ตรวจสอบว่าจ่ายไปแล้วหรือยัง
                boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
                        employee.getEmpId(), paymentMonth);

                if (exists) {
                    errorMessages.add(String.format("❌ %s - จ่ายไปแล้ว", employee.getEmpName()));
                    errorCount++;
                    continue;
                }

                BigDecimal dailyWage = BigDecimal.valueOf(employee.getDailyWage());
                BigDecimal totalSalary = dailyWage.multiply(BigDecimal.valueOf(workDays));

                EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
                payment.setEmployee(employee);
                payment.setPaymentMonth(paymentMonth);
                payment.setAmount(totalSalary);
                payment.setPaymentDate(paymentDate);
                payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
                payment.setType(EmployeeSalaryPayment.PaymentType.DAILY);
                payment.setWorkDays(workDays);

                EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);

                // สร้าง Transaction
                Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
                savedPayment.setTransactionId(transaction.getTransactionId());
                salaryPaymentRepository.save(savedPayment);

                totalPaid = totalPaid.add(totalSalary);
                successCount++;
                successMessages.add(String.format("✅ %s - %,.2f บาท (รายวัน %d วัน)",
                        employee.getEmpName(), totalSalary.doubleValue(), workDays));

            } catch (Exception e) {
                errorMessages.add(String.format("❌ %s - Error: %s",
                        employee.getEmpName(), e.getMessage()));
                errorCount++;
            }
        }

        result.put("success", errorCount == 0);
        result.put("successCount", successCount);
        result.put("errorCount", errorCount);
        result.put("totalPaid", totalPaid);
        result.put("successMessages", successMessages);
        result.put("errorMessages", errorMessages);

        return result;
    }

    /**
     * ✅ อัพเดทสถานะการจ่ายเงิน
     */
    public EmployeeSalaryPayment updatePaymentStatus(Long paymentId,
                                                     EmployeeSalaryPayment.PaymentStatus newStatus) {
        EmployeeSalaryPayment payment = salaryPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        EmployeeSalaryPayment.PaymentStatus oldStatus = payment.getStatus();
        payment.setStatus(newStatus);

        EmployeeSalaryPayment updatedPayment = salaryPaymentRepository.save(payment);

        // ถ้าเปลี่ยนจาก PENDING -> PAID ให้สร้าง Transaction
        if (oldStatus == EmployeeSalaryPayment.PaymentStatus.PENDING &&
                newStatus == EmployeeSalaryPayment.PaymentStatus.PAID) {

            Transaction transaction = transactionService.createSalaryPaymentTransaction(updatedPayment);
            updatedPayment.setTransactionId(transaction.getTransactionId());
            updatedPayment = salaryPaymentRepository.save(updatedPayment);
        }

        return updatedPayment;
    }

    // ============================================
    // ⭐ AUTO SCHEDULER - รันทุกวันที่ 25 เวลา 00:00
    // ============================================

    @Scheduled(cron = "0 0 0 25 * ?")
    public void autoProcessMonthlySalaries() {
        System.out.println("🔄 [AUTO] Starting monthly salary processing...");

        YearMonth currentMonth = YearMonth.now();
        LocalDateTime paymentDate = LocalDateTime.now();

        try {
            List<Employee> monthlyEmployees = employeeRepository.findAll().stream()
                    .filter(e -> "MONTHLY".equalsIgnoreCase(e.getEmpType()) &&
                            e.getStatus() == Employee.Status.ACTIVE)
                    .toList();

            int successCount = 0;
            int skipCount = 0;

            for (Employee employee : monthlyEmployees) {
                try {
                    boolean exists = salaryPaymentRepository.existsByEmployeeAndMonth(
                            employee.getEmpId(), currentMonth);

                    if (exists) {
                        System.out.println(String.format("⏭️ Skip: %s - Already paid", employee.getEmpName()));
                        skipCount++;
                        continue;
                    }

                    BigDecimal salaryAmount = BigDecimal.valueOf(employee.getMonthlySalary());

                    EmployeeSalaryPayment payment = new EmployeeSalaryPayment();
                    payment.setEmployee(employee);
                    payment.setPaymentMonth(currentMonth);
                    payment.setAmount(salaryAmount);
                    payment.setPaymentDate(paymentDate);
                    payment.setStatus(EmployeeSalaryPayment.PaymentStatus.PAID);
                    payment.setType(EmployeeSalaryPayment.PaymentType.MONTHLY);
                    payment.setNotes("Auto-processed on day 25");

                    EmployeeSalaryPayment savedPayment = salaryPaymentRepository.save(payment);

                    Transaction transaction = transactionService.createSalaryPaymentTransaction(savedPayment);
                    savedPayment.setTransactionId(transaction.getTransactionId());
                    salaryPaymentRepository.save(savedPayment);

                    System.out.println(String.format("✅ Paid: %s - %,.2f THB",
                            employee.getEmpName(), salaryAmount.doubleValue()));
                    successCount++;

                } catch (Exception e) {
                    System.err.println(String.format("❌ Error processing %s: %s",
                            employee.getEmpName(), e.getMessage()));
                }
            }

            System.out.println(String.format(
                    "✅ [AUTO] Salary processing completed. Success: %d, Skipped: %d, Total: %d",
                    successCount, skipCount, monthlyEmployees.size()));

        } catch (Exception e) {
            System.err.println("❌ [AUTO] Salary processing failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================================
    // Summary
    // ============================================

    @Transactional(readOnly = true)
    public Map<String, Object> getSalarySummary(YearMonth month) {
        Map<String, Object> summary = new HashMap<>();

        BigDecimal totalPaid = salaryPaymentRepository.getTotalPaidForMonth(month);
        List<EmployeeSalaryPayment> payments = salaryPaymentRepository.findByPaymentMonth(month);

        long monthlyCount = payments.stream()
                .filter(p -> p.getType() == EmployeeSalaryPayment.PaymentType.MONTHLY)
                .count();

        long dailyCount = payments.stream()
                .filter(p -> p.getType() == EmployeeSalaryPayment.PaymentType.DAILY)
                .count();

        summary.put("month", month);
        summary.put("totalPaid", totalPaid);
        summary.put("totalPayments", payments.size());
        summary.put("monthlyEmployeeCount", monthlyCount);
        summary.put("dailyEmployeeCount", dailyCount);

        return summary;
    }

    // ============================================
    // Helper Methods
    // ============================================

    private void validateSalaryPayment(EmployeeSalaryPayment payment) {
        if (payment.getEmployee() == null) {
            throw new IllegalArgumentException("Employee is required");
        }
        if (payment.getPaymentMonth() == null) {
            throw new IllegalArgumentException("Payment month is required");
        }
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}
