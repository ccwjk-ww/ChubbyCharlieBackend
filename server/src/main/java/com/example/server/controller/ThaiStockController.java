package com.example.server.controller;

import com.example.server.dto.ThaiStockDTO;
import com.example.server.entity.ThaiStock;
import com.example.server.mapper.StockMapper;
import com.example.server.service.ThaiStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/thai-stocks")
@CrossOrigin(origins = "*")
public class ThaiStockController {

    @Autowired
    private ThaiStockService thaiStockService;

    @Autowired
    private StockMapper stockMapper;

    @GetMapping
    public List<ThaiStockDTO> getAllThaiStocks() {
        return stockMapper.toThaiStockDTOList(thaiStockService.getAllThaiStocks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThaiStockDTO> getThaiStockById(@PathVariable Long id) {
        Optional<ThaiStock> thaiStock = thaiStockService.getThaiStockById(id);
        return thaiStock.map(stock -> ResponseEntity.ok(stockMapper.toThaiStockDTO(stock)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public List<ThaiStockDTO> getThaiStocksByStatus(@PathVariable String status) {
        ThaiStock.StockStatus stockStatus = ThaiStock.StockStatus.valueOf(status.toUpperCase());
        return stockMapper.toThaiStockDTOList(thaiStockService.getThaiStocksByStatus(stockStatus));
    }

    @GetMapping("/lot/{stockLotId}")
    public List<ThaiStockDTO> getThaiStocksByLot(@PathVariable Long stockLotId) {
        return stockMapper.toThaiStockDTOList(thaiStockService.getThaiStocksByLot(stockLotId));
    }

    @GetMapping("/lot/{stockLotId}/total-value")
    public ResponseEntity<Map<String, BigDecimal>> getTotalValueByLot(@PathVariable Long stockLotId) {
        BigDecimal totalValue = thaiStockService.getTotalValueByLot(stockLotId);
        return ResponseEntity.ok(Map.of("totalValue", totalValue != null ? totalValue : BigDecimal.ZERO));
    }

    @GetMapping("/search")
    public List<ThaiStockDTO> searchThaiStocks(@RequestParam String keyword) {
        return stockMapper.toThaiStockDTOList(thaiStockService.searchThaiStocks(keyword));
    }

    @PostMapping
    public ResponseEntity<ThaiStockDTO> createThaiStock(@RequestBody ThaiStock thaiStock) {
        try {
            ThaiStock created = thaiStockService.createThaiStock(thaiStock);
            return ResponseEntity.status(HttpStatus.CREATED).body(stockMapper.toThaiStockDTO(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ThaiStockDTO> updateThaiStock(@PathVariable Long id, @RequestBody ThaiStock thaiStockDetails) {
        try {
            ThaiStock updated = thaiStockService.updateThaiStock(id, thaiStockDetails);
            return ResponseEntity.ok(stockMapper.toThaiStockDTO(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ThaiStockDTO> updateThaiStockStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            if (statusStr == null) return ResponseEntity.badRequest().build();
            ThaiStock.StockStatus status = ThaiStock.StockStatus.valueOf(statusStr.toUpperCase());
            ThaiStock updated = thaiStockService.updateThaiStockStatus(id, status);
            return ResponseEntity.ok(stockMapper.toThaiStockDTO(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ============================================
    // ⭐ NEW: Defective Quantity Endpoints
    // ============================================

    /**
     * เพิ่มจำนวนของเสีย (สะสม)
     * PATCH /api/thai-stocks/{id}/defective
     * Body: { "count": 3 }
     */
    @PatchMapping("/{id}/defective")
    public ResponseEntity<?> recordDefective(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        try {
            Object countObj = body.get("count");
            if (countObj == null) return ResponseEntity.badRequest().body(Map.of("message", "count is required"));
            int count = Integer.parseInt(countObj.toString());
            if (count <= 0) return ResponseEntity.badRequest().body(Map.of("message", "count must be > 0"));
            String note = body.get("note") != null ? body.get("note").toString() : null;
            ThaiStock updated = thaiStockService.recordDefective(id, count, note);
            return ResponseEntity.ok(stockMapper.toThaiStockDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ตั้งค่าจำนวนของเสียโดยตรง (ใช้สำหรับ edit/reset)
     * PUT /api/thai-stocks/{id}/defective
     * Body: { "count": 5 }
     */
    @PutMapping("/{id}/defective")
    public ResponseEntity<?> setDefectiveQuantity(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        try {
            Integer count = body.get("count");
            if (count == null || count < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "count cannot be negative"));
            }
            ThaiStock updated = thaiStockService.setDefectiveQuantity(id, count);
            return ResponseEntity.ok(stockMapper.toThaiStockDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteThaiStock(@PathVariable Long id) {
        try {
            thaiStockService.deleteThaiStock(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}