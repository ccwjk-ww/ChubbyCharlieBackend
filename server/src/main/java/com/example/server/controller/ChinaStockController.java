package com.example.server.controller;

import com.example.server.dto.ChinaStockDTO;
import com.example.server.entity.ChinaStock;
import com.example.server.mapper.StockMapper;
import com.example.server.service.ChinaStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/china-stocks")
@CrossOrigin(origins = "*")
public class ChinaStockController {

    @Autowired
    private ChinaStockService chinaStockService;

    @Autowired
    private StockMapper stockMapper;

    @GetMapping
    public List<ChinaStockDTO> getAllChinaStocks() {
        return stockMapper.toChinaStockDTOList(chinaStockService.getAllChinaStocks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChinaStockDTO> getChinaStockById(@PathVariable Long id) {
        Optional<ChinaStock> chinaStock = chinaStockService.getChinaStockById(id);
        return chinaStock.map(stock -> ResponseEntity.ok(stockMapper.toChinaStockDTO(stock)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public List<ChinaStockDTO> getChinaStocksByStatus(@PathVariable String status) {
        ChinaStock.StockStatus stockStatus = ChinaStock.StockStatus.valueOf(status.toUpperCase());
        return stockMapper.toChinaStockDTOList(chinaStockService.getChinaStocksByStatus(stockStatus));
    }

    @GetMapping("/lot/{stockLotId}")
    public List<ChinaStockDTO> getChinaStocksByLot(@PathVariable Long stockLotId) {
        return stockMapper.toChinaStockDTOList(chinaStockService.getChinaStocksByLot(stockLotId));
    }

    @GetMapping("/lot/{stockLotId}/total-value")
    public ResponseEntity<Map<String, BigDecimal>> getTotalValueByLot(@PathVariable Long stockLotId) {
        BigDecimal totalValue = chinaStockService.getTotalValueByLot(stockLotId);
        return ResponseEntity.ok(Map.of("totalValue", totalValue != null ? totalValue : BigDecimal.ZERO));
    }

    @GetMapping("/search")
    public List<ChinaStockDTO> searchChinaStocks(@RequestParam String keyword) {
        return stockMapper.toChinaStockDTOList(chinaStockService.searchChinaStocks(keyword));
    }

    @PostMapping
    public ResponseEntity<ChinaStockDTO> createChinaStock(@RequestBody ChinaStock chinaStock) {
        try {
            ChinaStock created = chinaStockService.createChinaStock(chinaStock);
            return ResponseEntity.status(HttpStatus.CREATED).body(stockMapper.toChinaStockDTO(created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChinaStockDTO> updateChinaStock(@PathVariable Long id, @RequestBody ChinaStock chinaStockDetails) {
        try {
            ChinaStock updated = chinaStockService.updateChinaStock(id, chinaStockDetails);
            return ResponseEntity.ok(stockMapper.toChinaStockDTO(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ChinaStockDTO> updateChinaStockStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            if (statusStr == null) return ResponseEntity.badRequest().build();
            ChinaStock.StockStatus status = ChinaStock.StockStatus.valueOf(statusStr.toUpperCase());
            ChinaStock updated = chinaStockService.updateChinaStockStatus(id, status);
            return ResponseEntity.ok(stockMapper.toChinaStockDTO(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ============================================
    // ⭐ NEW: Defective Quantity Endpoints
    // ============================================

    /**
     * เพิ่มจำนวนของเสีย (สะสม)
     * PATCH /api/china-stocks/{id}/defective
     * Body: { "count": 3 }
     */
    // ⭐ แทนที่ @PatchMapping("/{id}/defective") ใน ChinaStockController ด้วย version นี้

    @PatchMapping("/{id}/defective")
    public ResponseEntity<?> recordDefective(@PathVariable Long id,
                                             @RequestBody Map<String, Object> body) {
        try {
            Object countObj = body.get("count");
            if (countObj == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "count is required"));
            }
            int count = Integer.parseInt(countObj.toString());
            if (count <= 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "count must be greater than 0"));
            }
            String note = body.get("note") != null ? body.get("note").toString() : null;
            ChinaStock updated = chinaStockService.recordDefective(id, count, note);
            return ResponseEntity.ok(stockMapper.toChinaStockDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ตั้งค่าจำนวนของเสียโดยตรง (ใช้สำหรับ edit/reset)
     * PUT /api/china-stocks/{id}/defective
     * Body: { "count": 5 }
     */
    // ⭐ แทนที่ @PutMapping("/{id}/defective") เช่นกัน (ไม่ตัดสต็อก — reset only)
    @PutMapping("/{id}/defective")
    public ResponseEntity<?> setDefectiveQuantity(@PathVariable Long id,
                                                  @RequestBody Map<String, Integer> body) {
        try {
            Integer count = body.get("count");
            if (count == null || count < 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "count cannot be negative"));
            }
            ChinaStock updated = chinaStockService.setDefectiveQuantity(id, count);
            return ResponseEntity.ok(stockMapper.toChinaStockDTO(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ============================================
    // ไม่เปลี่ยน endpoints เดิม
    // ============================================

    @PatchMapping("/lot/{stockLotId}/exchange-rate")
    public ResponseEntity<List<ChinaStockDTO>> updateExchangeRateForLot(
            @PathVariable Long stockLotId,
            @RequestBody Map<String, BigDecimal> exchangeRateUpdate) {
        try {
            BigDecimal exchangeRate = exchangeRateUpdate.get("exchangeRate");
            if (exchangeRate == null) return ResponseEntity.badRequest().build();
            List<ChinaStock> updatedStocks = chinaStockService.updateExchangeRateForLot(stockLotId, exchangeRate);
            return ResponseEntity.ok(stockMapper.toChinaStockDTOList(updatedStocks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/lot/{stockLotId}/distribute-shipping")
    public ResponseEntity<List<ChinaStockDTO>> distributeShippingCostsWithAmount(
            @PathVariable Long stockLotId,
            @RequestBody Map<String, BigDecimal> shippingData) {
        try {
            BigDecimal totalShipping = shippingData.get("totalShipping");
            if (totalShipping == null) return ResponseEntity.badRequest().build();
            List<ChinaStock> updatedStocks = chinaStockService.distributeShippingCosts(stockLotId, totalShipping);
            return ResponseEntity.ok(stockMapper.toChinaStockDTOList(updatedStocks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChinaStock(@PathVariable Long id) {
        try {
            chinaStockService.deleteChinaStock(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

}