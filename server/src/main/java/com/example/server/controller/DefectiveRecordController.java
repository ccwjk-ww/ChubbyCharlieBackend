package com.example.server.controller;

import com.example.server.dto.DefectiveRecordDTO;
import com.example.server.service.DefectiveRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/defective-records")
@CrossOrigin(origins = "*")
public class DefectiveRecordController {

    @Autowired
    private DefectiveRecordService defectiveRecordService;

    /**
     * GET /api/defective-records/stock/{stockItemId}
     * ดึงประวัติของเสียทั้งหมดของ stock item หนึ่งรายการ
     */
    @GetMapping("/stock/{stockItemId}")
    public ResponseEntity<List<DefectiveRecordDTO>> getRecordsByStockItem(
            @PathVariable Long stockItemId) {
        List<DefectiveRecordDTO> records =
                defectiveRecordService.getRecordsByStockItemId(stockItemId);
        return ResponseEntity.ok(records);
    }
}