package com.example.server.service;

import com.example.server.dto.CategoryBreakdownResponse;
import com.example.server.dto.MonthlyReportResponse;
import com.example.server.entity.*;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class TransactionService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    // เดือนภาษาไทย
    private static final String[] THAI_MONTHS = {
            "มกราคม", "กุมภาพันธ์", "มีนาคม", "เมษายน", "พฤษภาคม", "มิถุนายน",
            "กรกฎาคม", "สิงหาคม", "กันยายน", "ตุลาคม", "พฤศจิกายน", "ธันวาคม"
    };

    // ============================================
    // CRUD Operations
    // ============================================

    @Transactional(readOnly = true)
    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAllOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public Optional<Transaction> getTransactionById(Long id) {
        return transactionRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByType(Transaction.TransactionType type) {
        return transactionRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByCategory(Transaction.TransactionCategory category) {
        return transactionRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.findByDateRange(startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<Transaction> searchTransactions(String keyword) {
        return transactionRepository.searchTransactions(keyword);
    }

    /**
     * สร้าง Transaction แบบ Manual
     */
    public Transaction createManualTransaction(Transaction transaction, String username) {
        validateTransaction(transaction);

        transaction.setMode(Transaction.TransactionMode.MANUAL);
        transaction.setCreatedBy(username);

        if (transaction.getTransactionDate() == null) {
            transaction.setTransactionDate(LocalDateTime.now());
        }

        // ✅ Generate transaction number ถ้ายังไม่มี
        if (transaction.getTransactionNumber() == null || transaction.getTransactionNumber().trim().isEmpty()) {
            transaction.setTransactionNumber(generateTransactionNumber(transaction.getType()));
        }

        return transactionRepository.save(transaction);
    }

    /**
     * อัพเดท Transaction (เฉพาะ MANUAL เท่านั้น)
     */
    public Transaction updateTransaction(Long id, Transaction details) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));

        // ✅ ป้องกันการแก้ไข AUTO transaction
        if (transaction.getMode() == Transaction.TransactionMode.AUTO) {
            throw new IllegalStateException("Cannot edit auto-generated transactions");
        }

        // Update fields
        if (details.getType() != null) transaction.setType(details.getType());
        if (details.getCategory() != null) transaction.setCategory(details.getCategory());
        if (details.getAmount() != null) transaction.setAmount(details.getAmount());
        if (details.getDescription() != null) transaction.setDescription(details.getDescription());
        if (details.getTransactionDate() != null) transaction.setTransactionDate(details.getTransactionDate());
        if (details.getNotes() != null) transaction.setNotes(details.getNotes());

        validateTransaction(transaction);
        return transactionRepository.save(transaction);
    }

    /**
     * ลบ Transaction (เฉพาะ MANUAL เท่านั้น)
     */
    public void deleteTransaction(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (transaction.getMode() == Transaction.TransactionMode.AUTO) {
            throw new IllegalStateException("Cannot delete auto-generated transactions");
        }

        transactionRepository.deleteById(id);
    }

    // ============================================
    // AUTO Transaction Creation (จากระบบอื่น)
    // ============================================

    /**
     * ✅ สร้าง Transaction จากการชำระเงิน Order
     * เรียกใช้จาก OrderService เมื่อ PaymentStatus = PAID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createOrderPaymentTransaction(Order order) {
        Transaction transaction = new Transaction();

        // ✅ Generate transaction number
        transaction.setTransactionNumber(generateTransactionNumber(Transaction.TransactionType.INCOME));

        transaction.setType(Transaction.TransactionType.INCOME);
        transaction.setCategory(Transaction.TransactionCategory.ORDER_PAYMENT);
        transaction.setAmount(order.getNetAmount());
        transaction.setDescription("รายรับจากคำสั่งซื้อ: " + order.getOrderNumber());
        transaction.setOrderId(order.getOrderId());
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setMode(Transaction.TransactionMode.AUTO);
        transaction.setCreatedBy("SYSTEM");

        return transactionRepository.save(transaction);
    }

    /**
     * ✅ สร้าง Transaction จากการสร้าง StockLot
     * เรียกใช้จาก StockLotService เมื่อสร้าง lot ใหม่
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createStockPurchaseTransaction(StockLot stockLot, BigDecimal totalCost) {
        Transaction transaction = new Transaction();

        // ✅ Generate transaction number
        transaction.setTransactionNumber(generateTransactionNumber(Transaction.TransactionType.EXPENSE));

        transaction.setType(Transaction.TransactionType.EXPENSE);
        transaction.setCategory(Transaction.TransactionCategory.STOCK_PURCHASE);
        transaction.setAmount(totalCost);
        transaction.setDescription("ซื้อ Stock Lot: " + stockLot.getLotName());
        transaction.setStockLotId(stockLot.getStockLotId());
        transaction.setTransactionDate(stockLot.getImportDate() != null ?
                stockLot.getImportDate() : LocalDateTime.now());
        transaction.setMode(Transaction.TransactionMode.AUTO);
        transaction.setCreatedBy("SYSTEM");

        return transactionRepository.save(transaction);
    }

    /**
     * ✅ สร้าง Transaction จากการจ่ายเงินเดือน
     * เรียกใช้จาก SalaryService
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction createSalaryPaymentTransaction(EmployeeSalaryPayment salaryPayment) {
        Transaction transaction = new Transaction();

        // ✅ Generate transaction number
        transaction.setTransactionNumber(generateTransactionNumber(Transaction.TransactionType.EXPENSE));

        transaction.setType(Transaction.TransactionType.EXPENSE);

        // เลือก category ตาม type
        if (salaryPayment.getType() == EmployeeSalaryPayment.PaymentType.DAILY) {
            transaction.setCategory(Transaction.TransactionCategory.SALARY_DAILY);
        } else {
            transaction.setCategory(Transaction.TransactionCategory.SALARY_MONTHLY);
        }

        transaction.setAmount(salaryPayment.getAmount());
        transaction.setDescription(String.format("เงินเดือน%s: %s (%s)",
                salaryPayment.getType() == EmployeeSalaryPayment.PaymentType.DAILY ? "รายวัน" : "รายเดือน",
                salaryPayment.getEmployee().getEmpName(),
                salaryPayment.getPaymentMonth()));
        transaction.setEmployeeId(salaryPayment.getEmployee().getEmpId());
        transaction.setSalaryPaymentId(salaryPayment.getPaymentId());
        transaction.setTransactionDate(salaryPayment.getPaymentDate());
        transaction.setMode(Transaction.TransactionMode.AUTO);
        transaction.setCreatedBy("SYSTEM");

        return transactionRepository.save(transaction);
    }

    // ============================================
    // Summary & Reports
    // ============================================

    @Transactional(readOnly = true)
    public Map<String, Object> getTransactionSummary() {
        Map<String, Object> summary = new HashMap<>();

        BigDecimal totalIncome = transactionRepository.getTotalIncome();
        BigDecimal totalExpense = transactionRepository.getTotalExpense();
        BigDecimal netProfit = transactionRepository.getNetProfit();

        summary.put("totalIncome", totalIncome);
        summary.put("totalExpense", totalExpense);
        summary.put("netProfit", netProfit);
        summary.put("totalTransactions", transactionRepository.count());
        summary.put("incomeCount", transactionRepository.findByType(Transaction.TransactionType.INCOME).size());
        summary.put("expenseCount", transactionRepository.findByType(Transaction.TransactionType.EXPENSE).size());

        return summary;
    }

    /**
     * ⭐ ดึงรายงานรายเดือน (Monthly Report)
     * GET /api/transactions/reports/monthly?year=2025&month=11
     */
    @Transactional(readOnly = true)
    public MonthlyReportResponse getMonthlyReport(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        // ดึงข้อมูล transactions ในช่วงเดือนนี้
        List<Transaction> transactions = transactionRepository.findByDateRange(startDate, endDate);

        // สร้าง response
        String monthName = THAI_MONTHS[month - 1];
        MonthlyReportResponse report = new MonthlyReportResponse(monthName, year);

        // คำนวณยอดรวม
        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            if (t.getType() == Transaction.TransactionType.INCOME) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
            }
        }

        report.setTotalIncome(totalIncome);
        report.setTotalExpense(totalExpense);
        report.setNetAmount(totalIncome.subtract(totalExpense));
        report.setTransactionCount(transactions.size());

        // สร้าง category breakdown
        List<CategoryBreakdownResponse> breakdown = createCategoryBreakdown(transactions, totalIncome, totalExpense);
        report.setCategoryBreakdown(breakdown);

        return report;
    }

    /**
     * ⭐ ดึงรายงานทั้งปี (Yearly Report)
     * GET /api/transactions/reports/yearly/2025
     */
    @Transactional(readOnly = true)
    public List<MonthlyReportResponse> getYearlyReport(int year) {
        List<MonthlyReportResponse> yearlyReports = new ArrayList<>();

        // วนลูปทุกเดือนในปี
        for (int month = 1; month <= 12; month++) {
            MonthlyReportResponse monthlyReport = getMonthlyReport(year, month);
            yearlyReports.add(monthlyReport);
        }

        return yearlyReports;
    }

    /**
     * สร้าง Category Breakdown
     */
    private List<CategoryBreakdownResponse> createCategoryBreakdown(
            List<Transaction> transactions,
            BigDecimal totalIncome,
            BigDecimal totalExpense) {

        // Group by category และนับจำนวน + ยอดรวม
        Map<Transaction.TransactionCategory, CategoryBreakdownResponse> categoryMap = new HashMap<>();

        for (Transaction t : transactions) {
            Transaction.TransactionCategory category = t.getCategory();

            CategoryBreakdownResponse breakdown = categoryMap.getOrDefault(
                    category,
                    new CategoryBreakdownResponse(category.name(), BigDecimal.ZERO, 0)
            );

            breakdown.setAmount(breakdown.getAmount().add(t.getAmount()));
            breakdown.setCount(breakdown.getCount() + 1);

            categoryMap.put(category, breakdown);
        }

        // คำนวณเปอร์เซ็นต์
        List<CategoryBreakdownResponse> breakdownList = new ArrayList<>(categoryMap.values());

        for (CategoryBreakdownResponse breakdown : breakdownList) {
            // หา total ที่ใช้คำนวณเปอร์เซ็นต์
            Transaction.TransactionCategory category = Transaction.TransactionCategory.valueOf(breakdown.getCategory());
            boolean isIncome = isIncomeCategory(category);

            BigDecimal total = isIncome ? totalIncome : totalExpense;

            if (total.compareTo(BigDecimal.ZERO) > 0) {
                double percentage = breakdown.getAmount()
                        .divide(total, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
                breakdown.setPercentage(percentage);
            } else {
                breakdown.setPercentage(0.0);
            }
        }

        // เรียงลำดับตามยอดเงิน (มากไปน้อย)
        breakdownList.sort((a, b) -> b.getAmount().compareTo(a.getAmount()));

        return breakdownList;
    }

    /**
     * ตรวจสอบว่า category เป็น INCOME หรือไม่
     */
    private boolean isIncomeCategory(Transaction.TransactionCategory category) {
        return category == Transaction.TransactionCategory.ORDER_PAYMENT ||
                category == Transaction.TransactionCategory.SERVICE_INCOME ||
                category == Transaction.TransactionCategory.OTHER_INCOME;
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * ✅ Generate Transaction Number อัตโนมัติ
     * Format: TXN-IN-20251031001234 หรือ TXN-EX-20251031001234
     */
    private String generateTransactionNumber(Transaction.TransactionType type) {
        String prefix = type == Transaction.TransactionType.INCOME ? "TXN-IN" : "TXN-EX";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return prefix + "-" + timestamp;
    }

    private void validateTransaction(Transaction transaction) {
        if (transaction.getType() == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        if (transaction.getCategory() == null) {
            throw new IllegalArgumentException("Transaction category is required");
        }
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }
}