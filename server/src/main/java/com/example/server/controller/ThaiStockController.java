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
public class ThaiStockController {

    @Autowired
    private ThaiStockService thaiStockService;

    @Autowired
    private StockMapper stockMapper;

    // Get all Thai stocks
    @GetMapping
    public List<ThaiStockDTO> getAllThaiStocks() {
        List<ThaiStock> stocks = thaiStockService.getAllThaiStocks();
        return stockMapper.toThaiStockDTOList(stocks);
    }

    // Get Thai stock by ID
    @GetMapping("/{id}")
    public ResponseEntity<ThaiStockDTO> getThaiStockById(@PathVariable Long id) {
        Optional<ThaiStock> thaiStock = thaiStockService.getThaiStockById(id);
        return thaiStock.map(stock -> ResponseEntity.ok(stockMapper.toThaiStockDTO(stock)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get Thai stocks by status
    @GetMapping("/status/{status}")
    public List<ThaiStockDTO> getThaiStocksByStatus(@PathVariable String status) {
        try {
            ThaiStock.StockStatus stockStatus = ThaiStock.StockStatus.valueOf(status.toUpperCase());
            List<ThaiStock> stocks = thaiStockService.getThaiStocksByStatus(stockStatus);
            return stockMapper.toThaiStockDTOList(stocks);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status: " + status);
        }
    }

    // Get Thai stocks by lot ID
    @GetMapping("/lot/{stockLotId}")
    public List<ThaiStockDTO> getThaiStocksByLot(@PathVariable Long stockLotId) {
        List<ThaiStock> stocks = thaiStockService.getThaiStocksByLot(stockLotId);
        return stockMapper.toThaiStockDTOList(stocks);
    }

    // Get total value by lot
    @GetMapping("/lot/{stockLotId}/total-value")
    public ResponseEntity<Map<String, BigDecimal>> getTotalValueByLot(@PathVariable Long stockLotId) {
        BigDecimal totalValue = thaiStockService.getTotalValueByLot(stockLotId);
        return ResponseEntity.ok(Map.of("totalValue", totalValue != null ? totalValue : BigDecimal.ZERO));
    }

    // Search Thai stocks
    @GetMapping("/search")
    public List<ThaiStockDTO> searchThaiStocks(@RequestParam String keyword) {
        List<ThaiStock> stocks = thaiStockService.searchThaiStocks(keyword);
        return stockMapper.toThaiStockDTOList(stocks);
    }

    // Create new Thai stock
    @PostMapping
    public ResponseEntity<ThaiStockDTO> createThaiStock(@RequestBody ThaiStock thaiStock) {
        try {
            ThaiStock createdThaiStock = thaiStockService.createThaiStock(thaiStock);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(stockMapper.toThaiStockDTO(createdThaiStock));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Update Thai stock
    @PutMapping("/{id}")
    public ResponseEntity<ThaiStockDTO> updateThaiStock(@PathVariable Long id, @RequestBody ThaiStock thaiStockDetails) {
        try {
            ThaiStock updatedThaiStock = thaiStockService.updateThaiStock(id, thaiStockDetails);
            return ResponseEntity.ok(stockMapper.toThaiStockDTO(updatedThaiStock));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update Thai stock status only
    @PatchMapping("/{id}/status")
    public ResponseEntity<ThaiStockDTO> updateThaiStockStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            if (statusStr == null) {
                return ResponseEntity.badRequest().build();
            }

            ThaiStock.StockStatus status = ThaiStock.StockStatus.valueOf(statusStr.toUpperCase());
            ThaiStock updatedThaiStock = thaiStockService.updateThaiStockStatus(id, status);
            return ResponseEntity.ok(stockMapper.toThaiStockDTO(updatedThaiStock));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Delete Thai stock
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