package com.example.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ⭐ Configuration สำหรับเปิดใช้งาน @Scheduled
 * ทำให้ SalaryService.autoProcessMonthlySalaries() รันอัตโนมัติทุกวันที่ 25
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
    // This class enables @Scheduled annotations throughout the application
}