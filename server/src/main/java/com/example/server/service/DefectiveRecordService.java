package com.example.server.service;

import com.example.server.dto.DefectiveRecordDTO;
import com.example.server.entity.DefectiveRecord;
import com.example.server.respository.DefectiveRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefectiveRecordService {

    @Autowired
    private DefectiveRecordRepository defectiveRecordRepository;

    @Transactional
    public DefectiveRecord createRecord(Long stockItemId, int count,
                                        BigDecimal unitCost, String stockType, String note) {
        DefectiveRecord record = new DefectiveRecord();
        record.setStockItemId(stockItemId);
        record.setCount(count);
        record.setUnitCost(unitCost);
        record.setTotalValue(unitCost != null
                ? unitCost.multiply(BigDecimal.valueOf(count))
                : BigDecimal.ZERO);
        record.setRecordedAt(LocalDateTime.now());
        record.setStockType(stockType);
        record.setNote(note);
        return defectiveRecordRepository.save(record);
    }

    public List<DefectiveRecordDTO> getRecordsByStockItemId(Long stockItemId) {
        return defectiveRecordRepository
                .findByStockItemIdOrderByRecordedAtDesc(stockItemId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private DefectiveRecordDTO toDTO(DefectiveRecord r) {
        DefectiveRecordDTO dto = new DefectiveRecordDTO();
        dto.setRecordId(r.getRecordId());
        dto.setStockItemId(r.getStockItemId());
        dto.setCount(r.getCount());
        dto.setUnitCost(r.getUnitCost());
        dto.setTotalValue(r.getTotalValue());
        dto.setRecordedAt(r.getRecordedAt());
        dto.setNote(r.getNote());
        dto.setStockType(r.getStockType());
        return dto;
    }
}