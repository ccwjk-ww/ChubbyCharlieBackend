package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class DefectiveRecordDTO {
    private Long recordId;
    private Long stockItemId;
    private Integer count;
    private BigDecimal unitCost;
    private BigDecimal totalValue;
    private LocalDateTime recordedAt;
    private String note;
    private String stockType;
}