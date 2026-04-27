package com.example.server.respository;

import com.example.server.entity.StockDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StockDocumentRepository extends JpaRepository<StockDocument, Long> {
    List<StockDocument> findByStockItemIdOrderByUploadedAtDesc(Long stockItemId);
    void deleteByStockItemId(Long stockItemId);
    int countByStockItemId(Long stockItemId);
}