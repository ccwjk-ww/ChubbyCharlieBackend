// StockDocumentController.java
package com.example.server.controller;

import com.example.server.dto.StockDocumentDTO;
import com.example.server.entity.StockDocument;
import com.example.server.service.StockDocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-documents")
@CrossOrigin(origins = "*")
public class StockDocumentController {

    @Autowired
    private StockDocumentService stockDocumentService;

    /**
     * GET /api/stock-documents/stock/{stockItemId}
     * ดึงรายการเอกสารของ stock item
     */
    @GetMapping("/stock/{stockItemId}")
    public ResponseEntity<List<StockDocumentDTO>> getDocuments(@PathVariable Long stockItemId) {
        return ResponseEntity.ok(stockDocumentService.getDocumentsByStockItem(stockItemId));
    }

    /**
     * POST /api/stock-documents/stock/{stockItemId}/upload
     * อัปโหลดเอกสาร
     */
    @PostMapping("/stock/{stockItemId}/upload")
    public ResponseEntity<?> uploadDocument(
            @PathVariable Long stockItemId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        try {
            StockDocumentDTO dto = stockDocumentService.uploadDocument(stockItemId, file, description);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to upload file: " + e.getMessage()));
        }
    }

    /**
     * GET /api/stock-documents/{documentId}/download
     * ดาวน์โหลดไฟล์
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long documentId) {
        try {
            Path filePath = stockDocumentService.getFilePath(documentId);
            StockDocument doc = stockDocumentService.getDocument(documentId);
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = doc.getFileType() != null ? doc.getFileType() : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + doc.getOriginalName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * DELETE /api/stock-documents/{documentId}
     * ลบเอกสาร
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long documentId) {
        try {
            stockDocumentService.deleteDocument(documentId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Failed to delete file: " + e.getMessage()));
        }
    }
}