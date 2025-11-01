package com.example.server.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class OrderUploadRequestDTO {
    private String orderNumber;           // เลข PO ที่ user ใส่เอง
    private Long customerId;              // Customer ID ที่เลือกจาก dropdown
    private boolean autoDeductStock;      // ตัด stock อัตโนมัติหรือไม่
}