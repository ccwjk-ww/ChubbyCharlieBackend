package com.example.server.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@Data
public class MonthlySalaryProcessRequest {
    private YearMonth paymentMonth;
    private LocalDateTime paymentDate;
    private List<DailyEmployeeWorkDays> dailyEmployeeWorkDays;

    @Data
    public static class DailyEmployeeWorkDays {
        private Long employeeId;
        private Integer workDays;
    }
}
