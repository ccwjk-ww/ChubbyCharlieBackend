package com.example.server.service;

import com.example.server.dto.ChatRequest;
import com.example.server.dto.ChatResponse;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ⭐ SmartChatService V3 - รองรับ Top Selling Products & Yearly Summary
 * - จัดอันดับสินค้าขายดี
 * - สรุปยอดขายรายปี + รายเดือน
 * - สรุปการเงินรายปี + รายเดือน
 */
@Service
@RequiredArgsConstructor
public class SmartChatService {

    private final GeminiService geminiService;
    private final SystemDataService systemDataService;
    private final Gson gson = new Gson();

    /**
     * ⭐ Smart Chat - วิเคราะห์คำถามและดึงข้อมูลที่เกี่ยวข้องอัตโนมัติ
     */
    public Mono<ChatResponse> smartChat(String userMessage) {
        try {
            System.out.println("🔍 Analyzing query: " + userMessage);

            // 1. วิเคราะห์คำถาม
            QueryAnalysis analysis = analyzeQuery(userMessage);

            // 2. สร้าง Context จากข้อมูลระบบ (ดึงข้อมูลจริง!)
            String context = buildContext(analysis);

            System.out.println("📊 Context built with " + context.length() + " characters");

            // 3. สร้าง request สำหรับ Gemini
            ChatRequest request = new ChatRequest();
            request.setMessage(userMessage);
            request.setContext(context);

            // 4. เรียก Gemini API
            return geminiService.chat(request);

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.just(ChatResponse.error("เกิดข้อผิดพลาดในการวิเคราะห์คำถาม: " + e.getMessage()));
        }
    }

    /**
     * ⭐ วิเคราะห์คำถาม - เพิ่มการตรวจจับ Top Selling & Yearly Summary
     */
    private QueryAnalysis analyzeQuery(String query) {
        QueryAnalysis analysis = new QueryAnalysis();
        analysis.originalQuery = query;
        String lowerQuery = query.toLowerCase();

        // ตรวจจับคำสำคัญ
        analysis.needsProductData = containsAny(lowerQuery,
                "สินค้า", "product", "ผลิตภัณฑ์", "ของ", "มี", "กี่", "ชนิด");

        // ⭐ ตรวจจับคำถามเกี่ยวกับสินค้าขายดี
        analysis.needsTopSellingProducts = containsAny(lowerQuery,
                "ขายดี", "แรงกิ้ง", "อันดับ", "top", "ขายได้", "นิยม", "ยอดนิยม",
                "จัดอันดับ", "ranking", "best seller", "bestseller", "ขายเยอะ");

        analysis.needsStockData = containsAny(lowerQuery,
                "สต็อก", "stock", "คงเหลือ", "inventory", "วัตถุดิบ", "ใกล้หมด");

        analysis.needsStockForecast = containsAny(lowerQuery,
                "ใกล้หมด", "เร่งด่วน", "ควรสั่ง", "ต้องซื้อ", "forecast", "พยากรณ์", "คาดการณ์");

        analysis.needsOrderData = containsAny(lowerQuery,
                "คำสั่งซื้อ", "order", "ยอดขาย", "sales", "รายได้", "revenue");

        analysis.needsFinancialData = containsAny(lowerQuery,
                "รายรับ", "รายจ่าย", "กำไร", "income", "expense", "profit", "การเงิน",
                "financial", "สรุป", "transaction");

        analysis.needsEmployeeData = containsAny(lowerQuery,
                "พนักงาน", "employee", "คน", "ทำงาน", "staff");

        analysis.needsSalaryData = containsAny(lowerQuery,
                "เงินเดือน", "salary", "ค่าแรง", "จ่ายเงิน");

        // ⭐ ตรวจจับว่าต้องการข้อมูลทั้งปีหรือไม่
        analysis.needsYearlyData = containsAny(lowerQuery,
                "ทั้งปี", "ตั้งแต่", "ถึง", "รวมปี", "ย้อนหลัง", "ตลอดปี",
                "รายปี", "yearly", "annual", "ประจำปี", "สรุปปี", "เดือน 1", "เดือน 12");

        // ตรวจจับเดือน/ปี
        analysis.yearMonth = extractYearMonth(query);
        analysis.yearMonthRange = extractYearMonthRange(query);

        // ⭐ ตรวจจับปีที่ต้องการ
        analysis.targetYear = extractTargetYear(query);

        return analysis;
    }

    /**
     * ⭐ สร้าง Context - เพิ่ม Top Selling Products & Yearly Summary
     */
    private String buildContext(QueryAnalysis analysis) {
        StringBuilder context = new StringBuilder();
        context.append("📊 **ข้อมูลระบบ Chubby Charlie**\n");
        context.append("═══════════════════════════════════════\n\n");

        // 1. Product Data
        if (analysis.needsProductData) {
            try {
                SystemDataService.ProductData productData = systemDataService.getProductData();
                context.append("### 📦 ข้อมูลสินค้า\n");
                context.append(String.format("- สินค้าทั้งหมด: %d รายการ\n", productData.getTotalProducts()));
                context.append(String.format("- สินค้าที่ใช้งานอยู่: %d รายการ\n", productData.getActiveProducts()));
                context.append(String.format("- สินค้าที่ยกเลิก: %d รายการ\n", productData.getDiscontinuedProducts()));

                if (productData.getMostExpensiveProduct() != null) {
                    context.append(String.format("- สินค้าราคาสูงสุด: %s (%.2f บาท)\n",
                            productData.getMostExpensiveProduct(), productData.getMostExpensivePrice()));
                }

                if (!productData.getCategoryCounts().isEmpty()) {
                    context.append("- จำนวนตาม Category:\n");
                    productData.getCategoryCounts().forEach((category, count) ->
                            context.append(String.format("  • %s: %d รายการ\n",
                                    category != null && !category.isEmpty() ? category : "ไม่มีหมวดหมู่", count))
                    );
                }
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting product data: " + e.getMessage());
            }
        }

        // ⭐ 2. Top Selling Products
        if (analysis.needsTopSellingProducts) {
            try {
                int year = analysis.targetYear != null ? analysis.targetYear : LocalDate.now().getYear();
                SystemDataService.TopSellingProductsData topData =
                        systemDataService.getTopSellingProducts(year, 10);

                context.append(String.format("### 🏆 สินค้าขายดี Top 10 ประจำปี %d (พ.ศ. %d)\n",
                        year, year + 543));
                context.append(String.format("- จำนวนสินค้าที่มียอดขาย: %d รายการ\n\n",
                        topData.getTotalProductsSold()));

                if (!topData.getTopProducts().isEmpty()) {
                    context.append("**อันดับสินค้าขายดี:**\n");
                    int rank = 1;
                    for (SystemDataService.ProductSalesInfo product : topData.getTopProducts()) {
                        context.append(String.format("%d. %s (SKU: %s)\n",
                                rank, product.getProductName(), product.getProductSku()));
                        context.append(String.format("   - ขายได้: %d ชิ้น\n",
                                product.getTotalQuantitySold()));
                        context.append(String.format("   - รายได้: %.2f บาท\n",
                                product.getTotalRevenue()));
                        rank++;
                    }
                } else {
                    context.append("*ยังไม่มีข้อมูลยอดขายในปีนี้*\n");
                }
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting top selling products: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 3. Stock Data
        if (analysis.needsStockData) {
            try {
                SystemDataService.StockData stockData = systemDataService.getStockData();
                context.append("### 📦 ข้อมูลสต็อก\n");
                context.append(String.format("- Stock Items ทั้งหมด: %d รายการ\n", stockData.getTotalStockItems()));
                context.append(String.format("- China Stock: %d รายการ (มูลค่า %.2f บาท)\n",
                        stockData.getTotalChinaStocks(), stockData.getTotalChinaStockValue()));
                context.append(String.format("- Thai Stock: %d รายการ (มูลค่า %.2f บาท)\n",
                        stockData.getTotalThaiStocks(), stockData.getTotalThaiStockValue()));
                context.append(String.format("- มูลค่ารวมทั้งหมด: %.2f บาท\n", stockData.getTotalStockValue()));
                context.append(String.format("- Stock Lots: %d รายการ\n", stockData.getTotalStockLots()));
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting stock data: " + e.getMessage());
            }
        }

        // 4. Stock Forecast
        if (analysis.needsStockForecast) {
            try {
                SystemDataService.StockForecastData forecastData = systemDataService.getStockForecastData();
                context.append("### ⚠️ การพยากรณ์สต็อก\n");
                context.append(String.format("- Stock ที่ต้องสั่งซื้อเร่งด่วน: %d รายการ\n",
                        forecastData.getUrgentStockCount()));
                context.append(String.format("- Stock ที่จะหมดใน 7 วัน: %d รายการ\n",
                        forecastData.getStockRunningOutSoon()));
                context.append(String.format("- ต้นทุนที่ต้องสั่งซื้อเร่งด่วน: %.2f บาท\n",
                        forecastData.getEstimatedUrgentOrderCost()));

                if (!forecastData.getTop5NearEmptyItems().isEmpty()) {
                    context.append("- Top 5 Stock ที่ใกล้หมด:\n");
                    forecastData.getTop5NearEmptyItems().forEach(item ->
                            context.append(String.format("  • %s\n", item))
                    );
                }
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting forecast data: " + e.getMessage());
            }
        }

        // 5. Order Data
        if (analysis.needsOrderData && !analysis.needsYearlyData) {
            try {
                SystemDataService.OrderData orderData = systemDataService.getOrderData();
                context.append("### 📋 ข้อมูลคำสั่งซื้อ\n");
                context.append(String.format("- คำสั่งซื้อทั้งหมด: %d รายการ\n", orderData.getTotalOrders()));
                context.append(String.format("- ยอดขายรวม: %.2f บาท\n", orderData.getTotalSalesAmount()));

                if (!orderData.getOrderStatusCounts().isEmpty()) {
                    context.append("- จำนวนตาม Status:\n");
                    orderData.getOrderStatusCounts().forEach((status, count) ->
                            context.append(String.format("  • %s: %d รายการ\n", status.name(), count))
                    );
                }

                if (!orderData.getOrderSourceCounts().isEmpty()) {
                    context.append("- จำนวนตาม Source:\n");
                    orderData.getOrderSourceCounts().forEach((source, count) ->
                            context.append(String.format("  • %s: %d รายการ\n", source.name(), count))
                    );
                }
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting order data: " + e.getMessage());
            }
        }

        // ⭐ 6. Yearly Sales Data (ถ้าถามเรื่องยอดขายรายปี)
        if (analysis.needsOrderData && analysis.needsYearlyData) {
            try {
                int year = analysis.targetYear != null ? analysis.targetYear : LocalDate.now().getYear();
                SystemDataService.YearlySalesData yearlySales =
                        systemDataService.getYearlySalesData(year);

                context.append(String.format("### 📈 ยอดขายประจำปี %d (พ.ศ. %d)\n\n",
                        year, year + 543));

                context.append("**สรุปรวมทั้งปี:**\n");
                context.append(String.format("- 📦 คำสั่งซื้อทั้งหมด: %d รายการ\n",
                        yearlySales.getTotalOrders()));
                context.append(String.format("- 💰 รายได้รวม: %.2f บาท\n\n",
                        yearlySales.getTotalRevenue()));

                if (yearlySales.getBestMonth() != null) {
                    context.append(String.format("- 🏆 เดือนที่ขายดีที่สุด: เดือน %d (%.2f บาท)\n\n",
                            yearlySales.getBestMonth(), yearlySales.getBestMonthRevenue()));
                }

                context.append("**รายละเอียดแต่ละเดือน:**\n");
                yearlySales.getMonthlyBreakdown().forEach((month, breakdown) -> {
                    context.append(String.format("• เดือน %d: %d คำสั่งซื้อ, รายได้ %.2f บาท\n",
                            month, breakdown.getTotalOrders(), breakdown.getTotalRevenue()));
                });
                context.append("\n");

            } catch (Exception e) {
                System.err.println("Error getting yearly sales data: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ⭐ 7. Financial Data
        if (analysis.needsFinancialData) {
            try {
                // ⭐ ถ้าถามทั้งปี
                if (analysis.needsYearlyData) {
                    int year = analysis.targetYear != null ? analysis.targetYear : LocalDate.now().getYear();
                    SystemDataService.YearlyFinancialData yearlyFinancial =
                            systemDataService.getYearlyFinancialData(year);

                    context.append(String.format("### 💰 การเงินประจำปี %d (พ.ศ. %d)\n\n",
                            year, year + 543));

                    context.append("**สรุปรวมทั้งปี:**\n");
                    context.append(String.format("- 💰 รายรับทั้งหมด: %.2f บาท\n",
                            yearlyFinancial.getTotalIncome()));
                    context.append(String.format("- 💸 รายจ่ายทั้งหมด: %.2f บาท\n",
                            yearlyFinancial.getTotalExpense()));
                    context.append(String.format("- 📈 กำไรสุทธิ: %.2f บาท\n",
                            yearlyFinancial.getNetProfit()));
                    context.append(String.format("- 📊 Transaction ทั้งหมด: %d รายการ\n\n",
                            yearlyFinancial.getTotalTransactions()));

                    if (yearlyFinancial.getBestProfitMonth() != null) {
                        context.append(String.format("- 🏆 เดือนที่กำไรสูงสุด: เดือน %d (%.2f บาท)\n\n",
                                yearlyFinancial.getBestProfitMonth(), yearlyFinancial.getBestProfitAmount()));
                    }

                    context.append("**รายละเอียดแต่ละเดือน:**\n");
                    yearlyFinancial.getMonthlyBreakdown().forEach((month, breakdown) -> {
                        context.append(String.format("• เดือน %d: รายรับ %.2f | รายจ่าย %.2f | กำไร %.2f บาท\n",
                                month, breakdown.getIncome(), breakdown.getExpense(), breakdown.getProfit()));
                    });
                    context.append("\n");

                } else if (analysis.yearMonthRange != null) {
                    // ช่วงเดือน
                    int startMonth = analysis.yearMonthRange[0];
                    int endMonth = analysis.yearMonthRange[1];
                    int year = 2025;

                    context.append(String.format("### 💰 ข้อมูลการเงิน (เดือน %d-%d/2568)\n",
                            startMonth, endMonth));

                    BigDecimal totalIncome = BigDecimal.ZERO;
                    BigDecimal totalExpense = BigDecimal.ZERO;
                    BigDecimal totalProfit = BigDecimal.ZERO;
                    int totalTransactions = 0;

                    Map<String, BigDecimal> monthlyIncome = new LinkedHashMap<>();
                    Map<String, BigDecimal> monthlyExpense = new LinkedHashMap<>();

                    for (int month = startMonth; month <= endMonth; month++) {
                        try {
                            SystemDataService.MonthlyFinancialData monthData =
                                    systemDataService.getMonthlyFinancialData(year, month);

                            totalIncome = totalIncome.add(monthData.getTotalIncome());
                            totalExpense = totalExpense.add(monthData.getTotalExpense());
                            totalProfit = totalProfit.add(monthData.getNetProfit());
                            totalTransactions += monthData.getTransactionCount();

                            monthlyIncome.put(String.format("%d/%d", month, year + 543),
                                    monthData.getTotalIncome());
                            monthlyExpense.put(String.format("%d/%d", month, year + 543),
                                    monthData.getTotalExpense());

                        } catch (Exception e) {
                            System.err.println("Error getting data for month " + month + ": " + e.getMessage());
                        }
                    }

                    context.append(String.format("**สรุปรวม (เดือน %d-%d/2568):**\n", startMonth, endMonth));
                    context.append(String.format("- 💰 รายรับทั้งหมด: %.2f บาท\n", totalIncome));
                    context.append(String.format("- 💸 รายจ่ายทั้งหมด: %.2f บาท\n", totalExpense));
                    context.append(String.format("- 📈 กำไรสุทธิ: %.2f บาท\n", totalProfit));
                    context.append(String.format("- 📊 จำนวน Transaction: %d รายการ\n\n", totalTransactions));

                    context.append("**รายละเอียดแต่ละเดือน:**\n");
                    for (Map.Entry<String, BigDecimal> entry : monthlyIncome.entrySet()) {
                        String monthKey = entry.getKey();
                        BigDecimal income = entry.getValue();
                        BigDecimal expense = monthlyExpense.getOrDefault(monthKey, BigDecimal.ZERO);
                        BigDecimal profit = income.subtract(expense);

                        context.append(String.format("• %s: รายรับ %.2f บาท | รายจ่าย %.2f บาท | กำไร %.2f บาท\n",
                                monthKey, income, expense, profit));
                    }
                    context.append("\n");

                } else if (analysis.yearMonth != null) {
                    // รายเดือนเดียว
                    SystemDataService.MonthlyFinancialData financialData =
                            systemDataService.getMonthlyFinancialData(
                                    analysis.yearMonth.getYear(), analysis.yearMonth.getMonthValue());

                    context.append(String.format("### 💰 ข้อมูลการเงินเดือน %d/%d\n",
                            financialData.getMonth(), financialData.getYear() + 543));
                    context.append(String.format("- รายรับ: %.2f บาท\n", financialData.getTotalIncome()));
                    context.append(String.format("- รายจ่าย: %.2f บาท\n", financialData.getTotalExpense()));
                    context.append(String.format("- กำไรสุทธิ: %.2f บาท\n", financialData.getNetProfit()));
                    context.append(String.format("- จำนวน Transaction: %d รายการ\n",
                            financialData.getTransactionCount()));

                    if (!financialData.getCategoryCounts().isEmpty()) {
                        context.append("- จำนวนตาม Category:\n");
                        financialData.getCategoryCounts().forEach((category, count) ->
                                context.append(String.format("  • %s: %d รายการ\n", category.name(), count))
                        );
                    }
                    context.append("\n");
                }
            } catch (Exception e) {
                System.err.println("Error getting financial data: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // 8. Monthly Sales (รายเดือนเดียว)
        if (analysis.needsOrderData && analysis.yearMonth != null && !analysis.needsYearlyData) {
            try {
                SystemDataService.MonthlySalesData salesData = systemDataService.getMonthlySalesData(
                        analysis.yearMonth.getYear(), analysis.yearMonth.getMonthValue());

                context.append(String.format("### 📈 ยอดขายเดือน %d/%d\n",
                        salesData.getMonth(), salesData.getYear() + 543));
                context.append(String.format("- คำสั่งซื้อ: %d รายการ\n", salesData.getTotalOrders()));
                context.append(String.format("- รายได้รวม: %.2f บาท\n", salesData.getTotalRevenue()));

                if (!salesData.getOrderSourceCounts().isEmpty()) {
                    context.append("- แหล่งที่มา:\n");
                    salesData.getOrderSourceCounts().forEach((source, count) ->
                            context.append(String.format("  • %s: %d รายการ\n", source.name(), count))
                    );
                }
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting sales data: " + e.getMessage());
            }
        }

        // 9. Employee Data
        if (analysis.needsEmployeeData) {
            try {
                SystemDataService.EmployeeData employeeData = systemDataService.getEmployeeData();
                context.append("### 👥 ข้อมูลพนักงาน\n");
                context.append(String.format("- พนักงานทั้งหมด: %d คน\n", employeeData.getTotalEmployees()));
                context.append(String.format("- ทำงานอยู่: %d คน\n", employeeData.getActiveEmployees()));
                context.append(String.format("- ลาออก/ไม่ทำงาน: %d คน\n", employeeData.getInactiveEmployees()));
                context.append(String.format("- รายเดือน: %d คน\n", employeeData.getMonthlyEmployees()));
                context.append(String.format("- รายวัน: %d คน\n", employeeData.getDailyEmployees()));

                if (!employeeData.getRoleCounts().isEmpty()) {
                    context.append("- จำนวนตาม Role:\n");
                    employeeData.getRoleCounts().forEach((role, count) ->
                            context.append(String.format("  • %s: %d คน\n", role, count))
                    );
                }
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting employee data: " + e.getMessage());
            }
        }

        // 10. Salary Data
        if (analysis.needsSalaryData && analysis.yearMonth != null) {
            try {
                SystemDataService.MonthlySalaryData salaryData = systemDataService.getMonthlySalaryData(
                        analysis.yearMonth.getYear(), analysis.yearMonth.getMonthValue());

                context.append(String.format("### 💵 ข้อมูลเงินเดือนเดือน %d/%d\n",
                        salaryData.getMonth(), salaryData.getYear() + 543));
                context.append(String.format("- จ่ายแล้ว: %.2f บาท\n", salaryData.getTotalPaid()));
                context.append(String.format("- ค้างจ่าย: %.2f บาท\n", salaryData.getPendingAmount()));
                context.append(String.format("- จำนวนการจ่าย: %d รายการ\n", salaryData.getTotalPayments()));
                context.append(String.format("  • รายเดือน: %d รายการ\n", salaryData.getMonthlyPayments()));
                context.append(String.format("  • รายวัน: %d รายการ\n", salaryData.getDailyPayments()));
                context.append("\n");
            } catch (Exception e) {
                System.err.println("Error getting salary data: " + e.getMessage());
            }
        }

        context.append("═══════════════════════════════════════\n");
        context.append(String.format("📅 ข้อมูล ณ วันที่: %s\n",
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))));

        return context.toString();
    }

    /**
     * ตรวจสอบว่า text มีคำใดคำหนึ่งใน keywords หรือไม่
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * แยกเดือน/ปีจากคำถาม
     */
    private YearMonth extractYearMonth(String query) {
        try {
            // Pattern 1: MM/YYYY
            Pattern pattern1 = Pattern.compile("(\\d{1,2})/(\\d{4})");
            Matcher matcher1 = pattern1.matcher(query);
            if (matcher1.find()) {
                int month = Integer.parseInt(matcher1.group(1));
                int year = Integer.parseInt(matcher1.group(2));
                if (year > 2500) {
                    year -= 543;
                }
                return YearMonth.of(year, month);
            }

            // Pattern 2: เดือน XX ปี YYYY
            Pattern pattern2 = Pattern.compile("เดือน\\s*(\\d{1,2})\\s*(?:ปี)?\\s*(\\d{4})");
            Matcher matcher2 = pattern2.matcher(query);
            if (matcher2.find()) {
                int month = Integer.parseInt(matcher2.group(1));
                int year = Integer.parseInt(matcher2.group(2));
                if (year > 2500) {
                    year -= 543;
                }
                return YearMonth.of(year, month);
            }

        } catch (Exception e) {
            // ignore
        }

        return null;
    }

    /**
     * แยกช่วงเดือนจากคำถาม
     */
    private int[] extractYearMonthRange(String query) {
        try {
            Pattern pattern = Pattern.compile("เดือน\\s*(\\d{1,2})\\s*(?:ถึง|ถึงเดือน|ตั้งแต่เดือน)?\\s*(\\d{1,2})");
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                int startMonth = Integer.parseInt(matcher.group(1));
                int endMonth = Integer.parseInt(matcher.group(2));
                return new int[]{startMonth, endMonth};
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * ⭐ แยกปีจากคำถาม (สำหรับ Top Selling & Yearly Summary)
     */
    private Integer extractTargetYear(String query) {
        try {
            // Pattern: ปี YYYY หรือ พ.ศ. YYYY
            Pattern pattern = Pattern.compile("(?:ปี|พ\\.ศ\\.|พ\\.ศ|year)\\s*(\\d{4})");
            Matcher matcher = pattern.matcher(query);
            if (matcher.find()) {
                int year = Integer.parseInt(matcher.group(1));
                if (year > 2500) {
                    year -= 543;
                }
                return year;
            }

            // ถ้าไม่เจอ ให้ใช้ปีปัจจุบัน
            return LocalDate.now().getYear();

        } catch (Exception e) {
            return LocalDate.now().getYear();
        }
    }

    /**
     * Query Analysis Result
     */
    private static class QueryAnalysis {
        String originalQuery;
        boolean needsProductData = false;
        boolean needsTopSellingProducts = false;  // ⭐ NEW
        boolean needsStockData = false;
        boolean needsStockForecast = false;
        boolean needsOrderData = false;
        boolean needsFinancialData = false;
        boolean needsEmployeeData = false;
        boolean needsSalaryData = false;
        boolean needsYearlyData = false;
        YearMonth yearMonth = null;
        int[] yearMonthRange = null;
        Integer targetYear = null;  // ⭐ NEW
    }
}