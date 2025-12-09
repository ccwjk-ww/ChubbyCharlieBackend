package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ⭐ SystemDataService V2 - เพิ่ม Top Selling Products & Yearly Summary
 * - จัดอันดับสินค้าขายดี
 * - สรุปยอดขายรายปี + รายเดือน
 * - สรุปการเงินรายปี + รายเดือน
 */
@Service
@Transactional(readOnly = true)
public class SystemDataService {

    @Autowired private ProductRepository productRepository;
    @Autowired private ProductIngredientRepository productIngredientRepository;
    @Autowired private StockBaseRepository stockBaseRepository;
    @Autowired private ChinaStockRepository chinaStockRepository;
    @Autowired private ThaiStockRepository thaiStockRepository;
    @Autowired private StockLotRepository stockLotRepository;
    @Autowired private StockForecastRepository stockForecastRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private EmployeeSalaryPaymentRepository salaryPaymentRepository;
    @Autowired private CustomerRepository customerRepository;

    // ============================================
    // PRODUCT DATA
    // ============================================

    /**
     * ดึงข้อมูลสินค้าทั้งหมด
     */
    public ProductData getProductData() {
        ProductData data = new ProductData();

        List<Product> allProducts = productRepository.findAll();
        data.setTotalProducts(allProducts.size());
        data.setActiveProducts((int) allProducts.stream()
                .filter(p -> p.getStatus() == Product.ProductStatus.ACTIVE)
                .count());
        data.setDiscontinuedProducts((int) allProducts.stream()
                .filter(p -> p.getStatus() == Product.ProductStatus.DISCONTINUED)
                .count());

        // นับตาม category
        Map<String, Long> categoryCount = allProducts.stream()
                .filter(p -> p.getCategory() != null)
                .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));
        data.setCategoryCounts(categoryCount);

        // สินค้าที่มีราคาสูงสุด/ต่ำสุด
        allProducts.stream()
                .max(Comparator.comparing(Product::getSellingPrice, Comparator.nullsFirst(BigDecimal::compareTo)))
                .ifPresent(p -> {
                    data.setMostExpensiveProduct(p.getProductName());
                    data.setMostExpensivePrice(p.getSellingPrice());
                });

        allProducts.stream()
                .filter(p -> p.getSellingPrice() != null && p.getSellingPrice().compareTo(BigDecimal.ZERO) > 0)
                .min(Comparator.comparing(Product::getSellingPrice))
                .ifPresent(p -> {
                    data.setCheapestProduct(p.getProductName());
                    data.setCheapestPrice(p.getSellingPrice());
                });

        // คำนวณมูลค่ารวมของสินค้า
        BigDecimal totalValue = allProducts.stream()
                .map(p -> p.getSellingPrice() != null ? p.getSellingPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setTotalProductValue(totalValue);

        return data;
    }

    /**
     * ⭐ ดึงข้อมูลสินค้าขายดี Top N
     */
    public TopSellingProductsData getTopSellingProducts(int year, int limit) {
        TopSellingProductsData data = new TopSellingProductsData();
        data.setYear(year);
        data.setLimit(limit);

        LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        List<Order> yearOrders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // รวม OrderItems จากทุก Order
        Map<Long, ProductSalesInfo> productSalesMap = new HashMap<>();

        for (Order order : yearOrders) {
            // ข้าม cancelled/returned
            if (order.getStatus() == Order.OrderStatus.CANCELLED ||
                    order.getStatus() == Order.OrderStatus.RETURNED) {
                continue;
            }

            List<OrderItem> items = orderItemRepository.findByOrder(order);
            for (OrderItem item : items) {
                Long productId = item.getProduct().getProductId();

                ProductSalesInfo info = productSalesMap.getOrDefault(productId,
                        new ProductSalesInfo());
                info.setProductId(productId);
                info.setProductName(item.getProduct().getProductName());
                info.setProductSku(item.getProduct().getSku());
                info.setTotalQuantitySold(info.getTotalQuantitySold() + item.getQuantity());
                info.setTotalRevenue(info.getTotalRevenue().add(
                        item.getTotalPrice().multiply(BigDecimal.valueOf(item.getQuantity()))));

                productSalesMap.put(productId, info);
            }
        }

        // เรียงลำดับตามยอดขาย (quantity)
        List<ProductSalesInfo> topProducts = productSalesMap.values().stream()
                .sorted(Comparator.comparing(ProductSalesInfo::getTotalQuantitySold).reversed())
                .limit(limit)
                .collect(Collectors.toList());

        data.setTopProducts(topProducts);
        data.setTotalProductsSold(productSalesMap.size());

        return data;
    }

    // ============================================
    // STOCK DATA
    // ============================================

    /**
     * ดึงข้อมูล Stock ทั้งหมด
     */
    public StockData getStockData() {
        StockData data = new StockData();

        List<StockBase> allStocks = stockBaseRepository.findAll();
        List<ChinaStock> chinaStocks = chinaStockRepository.findAll();
        List<ThaiStock> thaiStocks = thaiStockRepository.findAll();
        List<StockLot> stockLots = stockLotRepository.findAll();

        data.setTotalStockItems(allStocks.size());
        data.setTotalChinaStocks(chinaStocks.size());
        data.setTotalThaiStocks(thaiStocks.size());
        data.setTotalStockLots(stockLots.size());

        // นับ Stock ตาม status
        data.setActiveStocks((int) allStocks.stream()
                .filter(s -> s.getStatus() == StockBase.StockStatus.ACTIVE)
                .count());
        data.setInactiveStocks((int) allStocks.stream()
                .filter(s -> s.getStatus() == StockBase.StockStatus.INACTIVE)
                .count());

        // คำนวณมูลค่า Stock รวม
        BigDecimal totalChinaValue = chinaStocks.stream()
                .map(ChinaStock::calculateTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalThaiValue = thaiStocks.stream()
                .map(ThaiStock::calculateTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        data.setTotalChinaStockValue(totalChinaValue);
        data.setTotalThaiStockValue(totalThaiValue);
        data.setTotalStockValue(totalChinaValue.add(totalThaiValue));

        // Stock Lots ตาม status
        Map<StockLot.StockStatus, Long> lotStatusCount = stockLots.stream()
                .collect(Collectors.groupingBy(StockLot::getStatus, Collectors.counting()));
        data.setLotStatusCounts(lotStatusCount);

        return data;
    }

    /**
     * ดึงข้อมูล Stock Forecast
     */
    public StockForecastData getStockForecastData() {
        StockForecastData data = new StockForecastData();

        List<StockForecast> allForecasts = stockForecastRepository.findAll();
        data.setTotalForecasts(allForecasts.size());

        // นับตาม urgency level
        Map<StockForecast.UrgencyLevel, Long> urgencyCounts = allForecasts.stream()
                .collect(Collectors.groupingBy(StockForecast::getUrgencyLevel, Collectors.counting()));
        data.setUrgencyLevelCounts(urgencyCounts);

        // รายการเร่งด่วน
        List<StockForecast> urgentItems = stockForecastRepository.findUrgentStockItems();
        data.setUrgentStockCount(urgentItems.size());

        // รายการที่จะหมดใน 7 วัน
        List<StockForecast> runningOut = stockForecastRepository.findStockRunningOutInDays(7);
        data.setStockRunningOutSoon(runningOut.size());

        // คำนวณต้นทุนที่ต้องสั่งซื้อเร่งด่วน
        BigDecimal criticalCost = urgentItems.stream()
                .map(f -> f.getEstimatedOrderCost() != null ? f.getEstimatedOrderCost() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setEstimatedUrgentOrderCost(criticalCost);

        // Top 5 items ที่ใกล้หมด
        List<String> top5NearEmpty = runningOut.stream()
                .limit(5)
                .map(f -> String.format("%s (เหลือ %d วัน)",
                        f.getStockItemName(), f.getDaysUntilStockOut()))
                .collect(Collectors.toList());
        data.setTop5NearEmptyItems(top5NearEmpty);

        return data;
    }

    // ============================================
    // ORDER & SALES DATA
    // ============================================

    /**
     * ดึงข้อมูล Orders
     */
    public OrderData getOrderData() {
        OrderData data = new OrderData();

        List<Order> allOrders = orderRepository.findAll();
        data.setTotalOrders(allOrders.size());

        // นับตาม status
        Map<Order.OrderStatus, Long> statusCounts = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
        data.setOrderStatusCounts(statusCounts);

        // นับตาม source
        Map<Order.OrderSource, Long> sourceCounts = allOrders.stream()
                .collect(Collectors.groupingBy(Order::getSource, Collectors.counting()));
        data.setOrderSourceCounts(sourceCounts);

        // คำนวณยอดขายรวม (ไม่รวม cancelled/returned)
        BigDecimal totalSales = allOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELLED &&
                        o.getStatus() != Order.OrderStatus.RETURNED)
                .map(o -> o.getNetAmount() != null ? o.getNetAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setTotalSalesAmount(totalSales);

        // Order ที่มียอดสูงสุด
        allOrders.stream()
                .max(Comparator.comparing(Order::getNetAmount, Comparator.nullsFirst(BigDecimal::compareTo)))
                .ifPresent(o -> {
                    data.setHighestOrderNumber(o.getOrderNumber());
                    data.setHighestOrderAmount(o.getNetAmount());
                });

        return data;
    }

    /**
     * ดึงข้อมูลยอดขายรายเดือน
     */
    public MonthlySalesData getMonthlySalesData(int year, int month) {
        MonthlySalesData data = new MonthlySalesData();
        data.setYear(year);
        data.setMonth(month);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Order> monthlyOrders = orderRepository.findByOrderDateBetween(startDate, endDate);

        data.setTotalOrders(monthlyOrders.size());

        // คำนวณยอดขาย (ไม่รวม cancelled/returned)
        BigDecimal monthlyRevenue = monthlyOrders.stream()
                .filter(o -> o.getStatus() != Order.OrderStatus.CANCELLED &&
                        o.getStatus() != Order.OrderStatus.RETURNED)
                .map(o -> o.getNetAmount() != null ? o.getNetAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setTotalRevenue(monthlyRevenue);

        // นับตาม source
        Map<Order.OrderSource, Long> sourceCounts = monthlyOrders.stream()
                .collect(Collectors.groupingBy(Order::getSource, Collectors.counting()));
        data.setOrderSourceCounts(sourceCounts);

        return data;
    }

    /**
     * ⭐ ดึงข้อมูลยอดขายรายปี (สรุปทั้งปี + แยกรายเดือน)
     */
    public YearlySalesData getYearlySalesData(int year) {
        YearlySalesData data = new YearlySalesData();
        data.setYear(year);

        LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year, 12, 31, 23, 59, 59);

        List<Order> yearOrders = orderRepository.findByOrderDateBetween(startDate, endDate);

        // สรุปรวมทั้งปี
        int totalOrders = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (Order order : yearOrders) {
            if (order.getStatus() != Order.OrderStatus.CANCELLED &&
                    order.getStatus() != Order.OrderStatus.RETURNED) {
                totalOrders++;
                totalRevenue = totalRevenue.add(
                        order.getNetAmount() != null ? order.getNetAmount() : BigDecimal.ZERO);
            }
        }

        data.setTotalOrders(totalOrders);
        data.setTotalRevenue(totalRevenue);

        // แยกรายเดือน
        Map<Integer, MonthlyBreakdown> monthlyBreakdown = new LinkedHashMap<>();
        for (int month = 1; month <= 12; month++) {
            MonthlySalesData monthData = getMonthlySalesData(year, month);

            MonthlyBreakdown breakdown = new MonthlyBreakdown();
            breakdown.setMonth(month);
            breakdown.setTotalOrders(monthData.getTotalOrders());
            breakdown.setTotalRevenue(monthData.getTotalRevenue());

            monthlyBreakdown.put(month, breakdown);
        }

        data.setMonthlyBreakdown(monthlyBreakdown);

        // เดือนที่มียอดขายสูงสุด
        monthlyBreakdown.values().stream()
                .max(Comparator.comparing(MonthlyBreakdown::getTotalRevenue))
                .ifPresent(m -> {
                    data.setBestMonth(m.getMonth());
                    data.setBestMonthRevenue(m.getTotalRevenue());
                });

        return data;
    }

    // ============================================
    // TRANSACTION & FINANCIAL DATA
    // ============================================

    /**
     * ดึงข้อมูลการเงินรายเดือน
     */
    public MonthlyFinancialData getMonthlyFinancialData(int year, int month) {
        MonthlyFinancialData data = new MonthlyFinancialData();
        data.setYear(year);
        data.setMonth(month);

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);

        List<Transaction> transactions = transactionRepository.findByDateRange(startDate, endDate);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Transaction t : transactions) {
            if (t.getType() == Transaction.TransactionType.INCOME) {
                totalIncome = totalIncome.add(t.getAmount());
            } else {
                totalExpense = totalExpense.add(t.getAmount());
            }
        }

        data.setTotalIncome(totalIncome);
        data.setTotalExpense(totalExpense);
        data.setNetProfit(totalIncome.subtract(totalExpense));
        data.setTransactionCount(transactions.size());

        // นับตาม category
        Map<Transaction.TransactionCategory, Long> categoryCounts = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()));
        data.setCategoryCounts(categoryCounts);

        return data;
    }

    /**
     * ⭐ ดึงข้อมูลการเงินรายปี (รายรับ-รายจ่าย-กำไรทั้งปี + แยกรายเดือน)
     */
    public YearlyFinancialData getYearlyFinancialData(int year) {
        YearlyFinancialData data = new YearlyFinancialData();
        data.setYear(year);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        int totalTransactions = 0;

        Map<Integer, MonthlyFinancialBreakdown> monthlyBreakdown = new LinkedHashMap<>();

        for (int month = 1; month <= 12; month++) {
            MonthlyFinancialData monthData = getMonthlyFinancialData(year, month);

            totalIncome = totalIncome.add(monthData.getTotalIncome());
            totalExpense = totalExpense.add(monthData.getTotalExpense());
            totalTransactions += monthData.getTransactionCount();

            MonthlyFinancialBreakdown breakdown = new MonthlyFinancialBreakdown();
            breakdown.setMonth(month);
            breakdown.setIncome(monthData.getTotalIncome());
            breakdown.setExpense(monthData.getTotalExpense());
            breakdown.setProfit(monthData.getNetProfit());
            breakdown.setTransactionCount(monthData.getTransactionCount());

            monthlyBreakdown.put(month, breakdown);
        }

        data.setTotalIncome(totalIncome);
        data.setTotalExpense(totalExpense);
        data.setNetProfit(totalIncome.subtract(totalExpense));
        data.setTotalTransactions(totalTransactions);
        data.setMonthlyBreakdown(monthlyBreakdown);

        // เดือนที่มีกำไรสูงสุด
        monthlyBreakdown.values().stream()
                .max(Comparator.comparing(MonthlyFinancialBreakdown::getProfit))
                .ifPresent(m -> {
                    data.setBestProfitMonth(m.getMonth());
                    data.setBestProfitAmount(m.getProfit());
                });

        return data;
    }

    // ============================================
    // EMPLOYEE & SALARY DATA
    // ============================================

    /**
     * ดึงข้อมูลพนักงาน
     */
    public EmployeeData getEmployeeData() {
        EmployeeData data = new EmployeeData();

        List<Employee> allEmployees = employeeRepository.findAll();
        data.setTotalEmployees(allEmployees.size());

        // นับตาม status
        data.setActiveEmployees((int) allEmployees.stream()
                .filter(e -> e.getStatus() == Employee.Status.ACTIVE)
                .count());
        data.setInactiveEmployees((int) allEmployees.stream()
                .filter(e -> e.getStatus() == Employee.Status.INACTIVE)
                .count());

        // นับตาม type
        data.setMonthlyEmployees((int) allEmployees.stream()
                .filter(e -> "MONTHLY".equalsIgnoreCase(e.getEmpType()))
                .count());
        data.setDailyEmployees((int) allEmployees.stream()
                .filter(e -> "DAILY".equalsIgnoreCase(e.getEmpType()))
                .count());

        // นับตาม role
        Map<String, Long> roleCounts = allEmployees.stream()
                .collect(Collectors.groupingBy(Employee::getRole, Collectors.counting()));
        data.setRoleCounts(roleCounts);

        return data;
    }

    /**
     * ดึงข้อมูลเงินเดือนรายเดือน
     */
    public MonthlySalaryData getMonthlySalaryData(int year, int month) {
        MonthlySalaryData data = new MonthlySalaryData();
        data.setYear(year);
        data.setMonth(month);

        YearMonth yearMonth = YearMonth.of(year, month);
        List<EmployeeSalaryPayment> payments = salaryPaymentRepository.findByPaymentMonth(yearMonth);

        data.setTotalPayments(payments.size());

        BigDecimal totalPaid = payments.stream()
                .filter(p -> p.getStatus() == EmployeeSalaryPayment.PaymentStatus.PAID)
                .map(EmployeeSalaryPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setTotalPaid(totalPaid);

        data.setMonthlyPayments((int) payments.stream()
                .filter(p -> p.getType() == EmployeeSalaryPayment.PaymentType.MONTHLY)
                .count());
        data.setDailyPayments((int) payments.stream()
                .filter(p -> p.getType() == EmployeeSalaryPayment.PaymentType.DAILY)
                .count());

        BigDecimal pending = payments.stream()
                .filter(p -> p.getStatus() == EmployeeSalaryPayment.PaymentStatus.PENDING)
                .map(EmployeeSalaryPayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setPendingAmount(pending);

        return data;
    }

    // ============================================
    // DATA CLASSES
    // ============================================

    @Data
    public static class ProductData {
        private int totalProducts;
        private int activeProducts;
        private int discontinuedProducts;
        private Map<String, Long> categoryCounts;
        private String mostExpensiveProduct;
        private BigDecimal mostExpensivePrice;
        private String cheapestProduct;
        private BigDecimal cheapestPrice;
        private BigDecimal totalProductValue;
    }

    /**
     * ⭐ ข้อมูลสินค้าขายดี
     */
    @Data
    public static class TopSellingProductsData {
        private int year;
        private int limit;
        private int totalProductsSold;
        private List<ProductSalesInfo> topProducts;
    }

    /**
     * ⭐ ข้อมูลยอดขายของแต่ละสินค้า
     */
    @Data
    public static class ProductSalesInfo {
        private Long productId;
        private String productName;
        private String productSku;
        private int totalQuantitySold = 0;
        private BigDecimal totalRevenue = BigDecimal.ZERO;
    }

    @Data
    public static class StockData {
        private int totalStockItems;
        private int totalChinaStocks;
        private int totalThaiStocks;
        private int totalStockLots;
        private int activeStocks;
        private int inactiveStocks;
        private BigDecimal totalStockValue;
        private BigDecimal totalChinaStockValue;
        private BigDecimal totalThaiStockValue;
        private Map<StockLot.StockStatus, Long> lotStatusCounts;
    }

    @Data
    public static class StockForecastData {
        private int totalForecasts;
        private Map<StockForecast.UrgencyLevel, Long> urgencyLevelCounts;
        private int urgentStockCount;
        private int stockRunningOutSoon;
        private BigDecimal estimatedUrgentOrderCost;
        private List<String> top5NearEmptyItems;
    }

    @Data
    public static class OrderData {
        private int totalOrders;
        private Map<Order.OrderStatus, Long> orderStatusCounts;
        private Map<Order.OrderSource, Long> orderSourceCounts;
        private BigDecimal totalSalesAmount;
        private String highestOrderNumber;
        private BigDecimal highestOrderAmount;
    }

    @Data
    public static class MonthlySalesData {
        private int year;
        private int month;
        private int totalOrders;
        private BigDecimal totalRevenue;
        private Map<Order.OrderSource, Long> orderSourceCounts;
    }

    /**
     * ⭐ ข้อมูลยอดขายรายปี
     */
    @Data
    public static class YearlySalesData {
        private int year;
        private int totalOrders;
        private BigDecimal totalRevenue;
        private Map<Integer, MonthlyBreakdown> monthlyBreakdown;
        private Integer bestMonth;
        private BigDecimal bestMonthRevenue;
    }

    /**
     * ⭐ สรุปยอดขายรายเดือน (สำหรับ Yearly Summary)
     */
    @Data
    public static class MonthlyBreakdown {
        private int month;
        private int totalOrders;
        private BigDecimal totalRevenue;
    }

    @Data
    public static class MonthlyFinancialData {
        private int year;
        private int month;
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal netProfit;
        private int transactionCount;
        private Map<Transaction.TransactionCategory, Long> categoryCounts;
    }

    /**
     * ⭐ ข้อมูลการเงินรายปี
     */
    @Data
    public static class YearlyFinancialData {
        private int year;
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal netProfit;
        private int totalTransactions;
        private Map<Integer, MonthlyFinancialBreakdown> monthlyBreakdown;
        private Integer bestProfitMonth;
        private BigDecimal bestProfitAmount;
    }

    /**
     * ⭐ สรุปการเงินรายเดือน (สำหรับ Yearly Summary)
     */
    @Data
    public static class MonthlyFinancialBreakdown {
        private int month;
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal profit;
        private int transactionCount;
    }

    @Data
    public static class EmployeeData {
        private int totalEmployees;
        private int activeEmployees;
        private int inactiveEmployees;
        private int monthlyEmployees;
        private int dailyEmployees;
        private Map<String, Long> roleCounts;
    }

    @Data
    public static class MonthlySalaryData {
        private int year;
        private int month;
        private int totalPayments;
        private BigDecimal totalPaid;
        private int monthlyPayments;
        private int dailyPayments;
        private BigDecimal pendingAmount;
    }
}