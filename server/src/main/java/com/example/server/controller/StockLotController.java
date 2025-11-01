//package com.example.server.controller;
//
//import com.example.server.dto.StockLotDTO;
//import com.example.server.entity.StockLot;
//import com.example.server.mapper.StockMapper;
//import com.example.server.service.StockLotService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.format.annotation.DateTimeFormat;
//
//import java.time.LocalDateTime;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/stock-lots")
//public class StockLotController {
//
//    @Autowired
//    private StockLotService stockLotService;
//
//    @Autowired
//    private StockMapper stockMapper;
//
//    // Get all stock lots
//    @GetMapping
//    public List<StockLotDTO> getAllStockLots() {
//        List<StockLot> stockLots = stockLotService.getAllStockLots();
//        return stockMapper.toStockLotDTOList(stockLots);
//    }
//
//    // Get stock lot by ID
//    @GetMapping("/{id}")
//    public ResponseEntity<StockLotDTO> getStockLotById(@PathVariable Long id) {
//        Optional<StockLot> stockLot = stockLotService.getStockLotById(id);
//        return stockLot.map(lot -> ResponseEntity.ok(stockMapper.toStockLotDTO(lot)))
//                .orElseGet(() -> ResponseEntity.notFound().build());
//    }
//
//    // Get stock lot by name
//    @GetMapping("/name/{lotName}")
//    public ResponseEntity<StockLotDTO> getStockLotByName(@PathVariable String lotName) {
//        Optional<StockLot> stockLot = stockLotService.getStockLotByName(lotName);
//        return stockLot.map(lot -> ResponseEntity.ok(stockMapper.toStockLotDTO(lot)))
//                .orElseGet(() -> ResponseEntity.notFound().build());
//    }
//
//    // Get stock lots by status
//    @GetMapping("/status/{status}")
//    public List<StockLotDTO> getStockLotsByStatus(@PathVariable String status) {
//        try {
//            StockLot.StockStatus stockStatus = StockLot.StockStatus.valueOf(status.toUpperCase());
//            List<StockLot> stockLots = stockLotService.getStockLotsByStatus(stockStatus);
//            return stockMapper.toStockLotDTOList(stockLots);
//        } catch (IllegalArgumentException e) {
//            throw new RuntimeException("Invalid status: " + status);
//        }
//    }
//
//    // Get stock lots by date range
//    @GetMapping("/date-range")
//    public List<StockLotDTO> getStockLotsByDateRange(
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
//            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
//            @RequestParam(defaultValue = "import") String dateType) {
//        List<StockLot> stockLots = stockLotService.getStockLotsByDateRange(startDate, endDate, dateType);
//        return stockMapper.toStockLotDTOList(stockLots);
//    }
//
//    // Create new stock lot
//    @PostMapping
//    public ResponseEntity<StockLotDTO> createStockLot(@RequestBody StockLot stockLot) {
//        try {
//            StockLot createdStockLot = stockLotService.createStockLot(stockLot);
//            return ResponseEntity.status(HttpStatus.CREATED)
//                    .body(stockMapper.toStockLotDTO(createdStockLot));
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.badRequest().build();
//        }
//    }
//
//    // Update stock lot
//    @PutMapping("/{id}")
//    public ResponseEntity<StockLotDTO> updateStockLot(@PathVariable Long id, @RequestBody StockLot stockLotDetails) {
//        try {
//            StockLot updatedStockLot = stockLotService.updateStockLot(id, stockLotDetails);
//            return ResponseEntity.ok(stockMapper.toStockLotDTO(updatedStockLot));
//        } catch (RuntimeException e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    // Update stock lot status only
//    @PatchMapping("/{id}/status")
//    public ResponseEntity<StockLotDTO> updateStockLotStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
//        try {
//            String statusStr = statusUpdate.get("status");
//            if (statusStr == null) {
//                return ResponseEntity.badRequest().build();
//            }
//
//            StockLot.StockStatus status = StockLot.StockStatus.valueOf(statusStr.toUpperCase());
//            StockLot updatedStockLot = stockLotService.updateStockLotStatus(id, status);
//            return ResponseEntity.ok(stockMapper.toStockLotDTO(updatedStockLot));
//        } catch (RuntimeException e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//
//    // Delete stock lot
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteStockLot(@PathVariable Long id) {
//        try {
//            stockLotService.deleteStockLot(id);
//            return ResponseEntity.noContent().build();
//        } catch (Exception e) {
//            return ResponseEntity.notFound().build();
//        }
//    }
//    @GetMapping("/options")
//    public List<Map<String, Object>> getStockLotOptions() {
//        List<StockLot> stockLots = stockLotService.getAllStockLots();
//        return stockLots.stream()
//                .map(lot -> {
//                    Map<String, Object> option = new HashMap<>();
//                    option.put("value", lot.getStockLotId());
//                    option.put("label", lot.getLotName());
//                    option.put("status", lot.getStatus().name());
//                    return option;
//                })
//                .collect(Collectors.toList());
//    }
//}
package com.example.server.controller;

import com.example.server.dto.StockLotDTO;
import com.example.server.entity.StockLot;
import com.example.server.mapper.StockMapper;
import com.example.server.service.StockLotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock-lots")
@CrossOrigin(origins = "*")
public class StockLotController {

    @Autowired
    private StockLotService stockLotService;

    @Autowired
    private StockMapper stockMapper;

    // Get all stock lots
    @GetMapping
    public List<StockLotDTO> getAllStockLots() {
        List<StockLot> stockLots = stockLotService.getAllStockLots();
        return stockMapper.toStockLotDTOList(stockLots);
    }

    // Get stock lot by ID
    @GetMapping("/{id}")
    public ResponseEntity<StockLotDTO> getStockLotById(@PathVariable Long id) {
        Optional<StockLot> stockLot = stockLotService.getStockLotById(id);
        return stockLot.map(lot -> ResponseEntity.ok(stockMapper.toStockLotDTO(lot)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get stock lot by name
    @GetMapping("/name/{lotName}")
    public ResponseEntity<StockLotDTO> getStockLotByName(@PathVariable String lotName) {
        Optional<StockLot> stockLot = stockLotService.getStockLotByName(lotName);
        return stockLot.map(lot -> ResponseEntity.ok(stockMapper.toStockLotDTO(lot)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Get stock lots by status
    @GetMapping("/status/{status}")
    public List<StockLotDTO> getStockLotsByStatus(@PathVariable String status) {
        try {
            StockLot.StockStatus stockStatus = StockLot.StockStatus.valueOf(status.toUpperCase());
            List<StockLot> stockLots = stockLotService.getStockLotsByStatus(stockStatus);
            return stockMapper.toStockLotDTOList(stockLots);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status: " + status);
        }
    }

    // Get stock lots by date range
    @GetMapping("/date-range")
    public List<StockLotDTO> getStockLotsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "import") String dateType) {
        List<StockLot> stockLots = stockLotService.getStockLotsByDateRange(startDate, endDate, dateType);
        return stockMapper.toStockLotDTOList(stockLots);
    }

    // Create new stock lot
    @PostMapping
    public ResponseEntity<StockLotDTO> createStockLot(@RequestBody StockLot stockLot) {
        try {
            StockLot createdStockLot = stockLotService.createStockLot(stockLot);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(stockMapper.toStockLotDTO(createdStockLot));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Update stock lot
    @PutMapping("/{id}")
    public ResponseEntity<?> updateStockLot(@PathVariable Long id, @RequestBody StockLot stockLotDetails) {
        try {
            StockLot updatedStockLot = stockLotService.updateStockLot(id, stockLotDetails);
            return ResponseEntity.ok(stockMapper.toStockLotDTO(updatedStockLot));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Update stock lot status only
    @PatchMapping("/{id}/status")
    public ResponseEntity<StockLotDTO> updateStockLotStatus(@PathVariable Long id, @RequestBody Map<String, String> statusUpdate) {
        try {
            String statusStr = statusUpdate.get("status");
            if (statusStr == null) {
                return ResponseEntity.badRequest().build();
            }

            StockLot.StockStatus status = StockLot.StockStatus.valueOf(statusStr.toUpperCase());
            StockLot updatedStockLot = stockLotService.updateStockLotStatus(id, status);
            return ResponseEntity.ok(stockMapper.toStockLotDTO(updatedStockLot));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ⭐ NEW: Complete Stock Lot และสร้าง Transaction อัตโนมัติ
     * POST /api/stock-lots/{id}/complete
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<?> completeStockLot(@PathVariable Long id) {
        try {
            StockLot completedLot = stockLotService.completeStockLot(id);

            // คำนวณยอดรวมเพื่อแสดงใน response
            BigDecimal totalCost = stockLotService.calculateTotalCost(completedLot);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Stock lot completed and transaction created successfully");
            response.put("stockLot", stockMapper.toStockLotDTO(completedLot));
            response.put("totalCost", totalCost);
            response.put("itemsCount", completedLot.getItems() != null ? completedLot.getItems().size() : 0);

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error completing stock lot: " + e.getMessage()
            ));
        }
    }

    // Delete stock lot
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStockLot(@PathVariable Long id) {
        try {
            stockLotService.deleteStockLot(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/options")
    public List<Map<String, Object>> getStockLotOptions() {
        List<StockLot> stockLots = stockLotService.getAllStockLots();
        return stockLots.stream()
                .map(lot -> {
                    Map<String, Object> option = new HashMap<>();
                    option.put("value", lot.getStockLotId());
                    option.put("label", lot.getLotName());
                    option.put("status", lot.getStatus().name());
                    return option;
                })
                .collect(Collectors.toList());
    }
}