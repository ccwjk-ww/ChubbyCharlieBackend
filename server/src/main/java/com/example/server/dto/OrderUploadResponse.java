package com.example.server.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Data
public class OrderUploadResponse {
    private boolean success;
    private String message;
    private Long orderId;
    private String orderNumber;
    private Integer itemsProcessed;
    private List<String> warnings;
    private List<String> errors;
    private List<String> stockDeductionMessages;
}