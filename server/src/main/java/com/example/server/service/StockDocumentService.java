// StockDocumentService.java
package com.example.server.service;

import com.example.server.dto.StockDocumentDTO;
import com.example.server.entity.StockDocument;
import com.example.server.respository.StockDocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class StockDocumentService {

    @Autowired
    private StockDocumentRepository stockDocumentRepository;

    // เปลี่ยน path ตามเครื่อง server ของคุณ
    @Value("${app.upload.dir:uploads/stock-documents}")
    private String uploadDir;

    // ============================================
    // Upload
    // ============================================

    @Transactional
    public StockDocumentDTO uploadDocument(Long stockItemId,
                                           MultipartFile file,
                                           String description) throws IOException {
        // Validate
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");

        long maxSize = 20 * 1024 * 1024L; // 20 MB
        if (file.getSize() > maxSize) throw new IllegalArgumentException("File size exceeds 20MB limit");

        // สร้าง directory ถ้ายังไม่มี
        Path uploadPath = Paths.get(uploadDir, String.valueOf(stockItemId));
        Files.createDirectories(uploadPath);

        // สร้างชื่อไฟล์ unique
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueFileName = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;

        // บันทึกไฟล์
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // บันทึก DB
        StockDocument doc = new StockDocument();
        doc.setStockItemId(stockItemId);
        doc.setFileName(uniqueFileName);
        doc.setOriginalName(originalName);
        doc.setFileType(file.getContentType());
        doc.setFileSize(file.getSize());
        doc.setDescription(description);
        doc.setUploadedAt(LocalDateTime.now());

        StockDocument saved = stockDocumentRepository.save(doc);
        return toDTO(saved);
    }

    // ============================================
    // Get List
    // ============================================

    public List<StockDocumentDTO> getDocumentsByStockItem(Long stockItemId) {
        return stockDocumentRepository
                .findByStockItemIdOrderByUploadedAtDesc(stockItemId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // ============================================
    // Download — คืน Path ของไฟล์
    // ============================================

    public Path getFilePath(Long documentId) {
        StockDocument doc = stockDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        return Paths.get(uploadDir, String.valueOf(doc.getStockItemId()), doc.getFileName());
    }

    public StockDocument getDocument(Long documentId) {
        return stockDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
    }

    // ============================================
    // Delete
    // ============================================

    @Transactional
    public void deleteDocument(Long documentId) throws IOException {
        StockDocument doc = stockDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        // ลบไฟล์จริง
        Path filePath = Paths.get(uploadDir, String.valueOf(doc.getStockItemId()), doc.getFileName());
        Files.deleteIfExists(filePath);

        // ลบ DB
        stockDocumentRepository.deleteById(documentId);
    }

    // ============================================
    // Mapper
    // ============================================

    private StockDocumentDTO toDTO(StockDocument doc) {
        StockDocumentDTO dto = new StockDocumentDTO();
        dto.setDocumentId(doc.getDocumentId());
        dto.setStockItemId(doc.getStockItemId());
        dto.setFileName(doc.getFileName());
        dto.setOriginalName(doc.getOriginalName());
        dto.setFileType(doc.getFileType());
        dto.setFileSize(doc.getFileSize());
        dto.setDescription(doc.getDescription());
        dto.setUploadedAt(doc.getUploadedAt());
        dto.setDownloadUrl("/api/stock-documents/" + doc.getDocumentId() + "/download");
        return dto;
    }

    public String formatFileSize(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}