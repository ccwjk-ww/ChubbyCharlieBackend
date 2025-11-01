package com.example.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyReportResponse {
    private String month;                    // "มกราคม", "กุมภาพันธ์", etc.
    private Integer year;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netAmount;
    private Integer transactionCount;
    private List<CategoryBreakdownResponse> categoryBreakdown;

    public MonthlyReportResponse(String month, Integer year) {
        this.month = month;
        this.year = year;
        this.totalIncome = BigDecimal.ZERO;
        this.totalExpense = BigDecimal.ZERO;
        this.netAmount = BigDecimal.ZERO;
        this.transactionCount = 0;
        this.categoryBreakdown = new ArrayList<>();
    }
}