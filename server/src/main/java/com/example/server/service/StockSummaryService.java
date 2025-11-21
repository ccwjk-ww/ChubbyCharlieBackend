package com.example.server.service;

import com.example.server.entity.ChinaStock;
import com.example.server.entity.ThaiStock;
import com.example.server.entity.StockLot;
import com.example.server.entity.StockBase;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StockSummaryService {

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    /**
     * ⭐ แก้ไข: Get summary statistics for a stock lot (รวม Buffer)
     */
    public Map<String, Object> getStockLotSummary(Long stockLotId) {
        Map<String, Object> summary = new HashMap<>();

        StockLot stockLot = stockLotRepository.findById(stockLotId)
                .orElseThrow(() -> new RuntimeException("Stock Lot not found"));

        List<StockBase> items = stockLot.getItems();

        if (items == null || items.isEmpty()) {
            summary.put("stockLotId", stockLotId);
            summary.put("chinaTotalValue", BigDecimal.ZERO);
            summary.put("chinaItemCount", 0L);
            summary.put("chinaTotalQuantity", 0);
            summary.put("thaiTotalValue", BigDecimal.ZERO);
            summary.put("thaiItemCount", 0L);
            summary.put("thaiTotalQuantity", 0);
            summary.put("grandTotalValue", BigDecimal.ZERO);
            summary.put("totalItemCount", 0L);
            summary.put("totalQuantity", 0);
            return summary;
        }

        BigDecimal chinaTotalValue = BigDecimal.ZERO;
        long chinaItemCount = 0;
        int chinaTotalQuantity = 0;

        BigDecimal thaiTotalValue = BigDecimal.ZERO;
        long thaiItemCount = 0;
        int thaiTotalQuantity = 0;

        for (StockBase item : items) {
            if (item instanceof ChinaStock) {
                ChinaStock chinaStock = (ChinaStock) item;

                // ⭐ ใช้ calculateTotalCost() ที่รวม Buffer แล้ว
                chinaTotalValue = chinaTotalValue.add(chinaStock.calculateTotalCost());
                chinaItemCount++;
                chinaTotalQuantity += (chinaStock.getQuantity() != null ? chinaStock.getQuantity() : 0);

            } else if (item instanceof ThaiStock) {
                ThaiStock thaiStock = (ThaiStock) item;

                // ⭐ ใช้ calculateTotalCost() ที่รวม Buffer แล้ว
                thaiTotalValue = thaiTotalValue.add(thaiStock.calculateTotalCost());
                thaiItemCount++;
                thaiTotalQuantity += (thaiStock.getQuantity() != null ? thaiStock.getQuantity() : 0);
            }
        }

        summary.put("stockLotId", stockLotId);
        summary.put("chinaTotalValue", chinaTotalValue);
        summary.put("chinaItemCount", chinaItemCount);
        summary.put("chinaTotalQuantity", chinaTotalQuantity);
        summary.put("thaiTotalValue", thaiTotalValue);
        summary.put("thaiItemCount", thaiItemCount);
        summary.put("thaiTotalQuantity", thaiTotalQuantity);

        BigDecimal grandTotal = chinaTotalValue.add(thaiTotalValue);
        summary.put("grandTotalValue", grandTotal);
        summary.put("totalItemCount", chinaItemCount + thaiItemCount);
        summary.put("totalQuantity", chinaTotalQuantity + thaiTotalQuantity);

        return summary;
    }

    /**
     * ⭐ แก้ไข: Get overall system summary (รวม Buffer)
     */
    public Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new HashMap<>();

        // Count totals
        Long totalLots = stockLotRepository.count();
        Long totalChinaItems = chinaStockRepository.count();
        Long totalThaiItems = thaiStockRepository.count();

        // Active counts
        Long activeChinaItems = chinaStockRepository.findByStatus(ChinaStock.StockStatus.ACTIVE).stream().count();
        Long activeThaiItems = thaiStockRepository.findByStatus(ThaiStock.StockStatus.ACTIVE).stream().count();

        // ⭐ คำนวณ Total Inventory Value (รวม Buffer)
        List<ChinaStock> allChinaStocks = chinaStockRepository.findAll();
        List<ThaiStock> allThaiStocks = thaiStockRepository.findAll();

        BigDecimal totalInventoryValue = BigDecimal.ZERO;

        // คำนวณจาก China Stocks
        for (ChinaStock chinaStock : allChinaStocks) {
            totalInventoryValue = totalInventoryValue.add(chinaStock.calculateTotalCost());
        }

        // คำนวณจาก Thai Stocks
        for (ThaiStock thaiStock : allThaiStocks) {
            totalInventoryValue = totalInventoryValue.add(thaiStock.calculateTotalCost());
        }

        summary.put("totalLots", totalLots);
        summary.put("totalChinaItems", totalChinaItems);
        summary.put("totalThaiItems", totalThaiItems);
        summary.put("totalItems", totalChinaItems + totalThaiItems);
        summary.put("activeChinaItems", activeChinaItems);
        summary.put("activeThaiItems", activeThaiItems);
        summary.put("activeItems", activeChinaItems + activeThaiItems);

        // ⭐ เพิ่ม Total Inventory Value
        summary.put("totalInventoryValue", totalInventoryValue);

        return summary;
    }
}