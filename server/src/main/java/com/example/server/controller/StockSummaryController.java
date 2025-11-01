package com.example.server.controller;

import com.example.server.service.StockSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/summary")
@CrossOrigin(origins = "*")
public class StockSummaryController {

    @Autowired
    private StockSummaryService stockSummaryService;

    // Get summary for a specific stock lot
    @GetMapping("/lot/{stockLotId}")
    public ResponseEntity<Map<String, Object>> getStockLotSummary(@PathVariable Long stockLotId) {
        Map<String, Object> summary = stockSummaryService.getStockLotSummary(stockLotId);
        return ResponseEntity.ok(summary);
    }

    // Get overall system summary
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemSummary() {
        Map<String, Object> summary = stockSummaryService.getSystemSummary();
        return ResponseEntity.ok(summary);
    }
}