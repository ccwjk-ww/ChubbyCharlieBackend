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

    // Get all China stocks
    @GetMapping
    public List<ChinaStockDTO> getAllChinaStocks() {
        List<ChinaStock> stocks = chinaStockService.getAllChinaStocks();
        return stockMapper.toChinaStockDTOList(stocks);
    }

    // Get China stock by ID
    @GetMapping("/{id}")
    public ResponseEntity<ChinaStockDTO> getChinaStockById(@PathVariable Long id) {
        Optional<ChinaStock> chinaStock = chinaStockService.getChinaStockById(id);
        return chinaStock.map(stock -> ResponseEntity.ok(stockMapper.toChinaStockDTO(stock)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get China stocks by status
    @GetMapping("/status/{status}")
    public List<ChinaStockDTO> getChinaStocksByStatus(@PathVariable String status) {
        try {
            ChinaStock.StockStatus stockStatus = ChinaStock.StockStatus.valueOf(status.toUpperCase());
            List<ChinaStock> stocks = chinaStockService.getChinaStocksByStatus(stockStatus);
            return stockMapper.toChinaStockDTOList(stocks);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status: " + status);
        }
    }

    // Get China stocks by lot ID
    @GetMapping("/lot/{stockLotId}")
    public List<ChinaStockDTO> getChinaStocksByLot(@PathVariable Long stockLotId) {
        List<ChinaStock> stocks = chinaStockService.getChinaStocksByLot(stockLotId);
        return stockMapper.toChinaStockDTOList(stocks);
    }

    // Get total value by lot
    @GetMapping("/lot/{stockLotId}/total-value")
    public ResponseEntity<Map<String, BigDecimal>> getTotalValueByLot(@PathVariable Long stockLotId) {
        BigDecimal totalValue = chinaStockService.getTotalValueByLot(stockLotId);
        return ResponseEntity.ok(Map.of("totalValue", totalValue != null ? totalValue : BigDecimal.ZERO));
    }

    // Search China stocks
    @GetMapping("/search")
    public List<ChinaStockDTO> searchChinaStocks(@RequestParam String keyword) {
        List<ChinaStock> stocks = chinaStockService.searchChinaStocks(keyword);
        return stockMapper.toChinaStockDTOList(stocks);
    }

    // Create new China stock
    @PostMapping
    public ResponseEntity<ChinaStockDTO> createChinaStock(@RequestBody ChinaStock chinaStock) {
        try {
            ChinaStock createdChinaStock = chinaStockService.createChinaStock(chinaStock);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(stockMapper.toChinaStockDTO(createdChinaStock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Update China stock
    @PutMapping("/{id}")
    public ResponseEntity<ChinaStockDTO> updateChinaStock(@PathVariable Long id, @RequestBody ChinaStock chinaStockDetails) {
        try {
            ChinaStock updatedChinaStock = chinaStockService.updateChinaStock(id, chinaStockDetails);
            return ResponseEntity.ok(stockMapper.toChinaStockDTO(updatedChinaStock));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update China stock status only
    @PatchMapping("/{id}/status")
    public ResponseEntity<ChinaStockDTO> updateChinaStockStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            if (statusStr == null) {
                return ResponseEntity.badRequest().build();
            }

            ChinaStock.StockStatus status = ChinaStock.StockStatus.valueOf(statusStr.toUpperCase());
            ChinaStock updatedChinaStock = chinaStockService.updateChinaStockStatus(id, status);
            return ResponseEntity.ok(stockMapper.toChinaStockDTO(updatedChinaStock));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Batch update exchange rate for a lot
    @PatchMapping("/lot/{stockLotId}/exchange-rate")
    public ResponseEntity<List<ChinaStockDTO>> updateExchangeRateForLot(
            @PathVariable Long stockLotId,
            @RequestBody Map<String, BigDecimal> exchangeRateUpdate) {
        try {
            BigDecimal exchangeRate = exchangeRateUpdate.get("exchangeRate");
            if (exchangeRate == null) {
                return ResponseEntity.badRequest().build();
            }

            List<ChinaStock> updatedStocks = chinaStockService.updateExchangeRateForLot(stockLotId, exchangeRate);
            return ResponseEntity.ok(stockMapper.toChinaStockDTOList(updatedStocks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ⭐ ลบ method นี้ออก - ไม่ใช้แล้ว
    // @PostMapping("/lot/{stockLotId}/distribute-shipping")
    // public ResponseEntity<List<ChinaStockDTO>> distributeShippingCosts(@PathVariable Long stockLotId) { ... }

    /**
     * ⭐ Distribute shipping costs with amount
     * POST /api/china-stocks/lot/{stockLotId}/distribute-shipping
     * Body: { "totalShipping": 1000.50 }
     */
    @PostMapping("/lot/{stockLotId}/distribute-shipping")
    public ResponseEntity<List<ChinaStockDTO>> distributeShippingCostsWithAmount(
            @PathVariable Long stockLotId,
            @RequestBody Map<String, BigDecimal> shippingData) {
        try {
            BigDecimal totalShipping = shippingData.get("totalShipping");
            if (totalShipping == null) {
                return ResponseEntity.badRequest().build();
            }

            List<ChinaStock> updatedStocks = chinaStockService.distributeShippingCosts(stockLotId, totalShipping);
            return ResponseEntity.ok(stockMapper.toChinaStockDTOList(updatedStocks));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Delete China stock
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