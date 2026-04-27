package com.example.server.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StockDocumentDTO {
    private Long documentId;
    private Long stockItemId;
    private String fileName;
    private String originalName;
    private String fileType;
    private Long fileSize;
    private String description;
    private LocalDateTime uploadedAt;
    private String downloadUrl;
}