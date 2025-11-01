package com.example.server.service;

import com.example.server.entity.ChinaStock;
import com.example.server.entity.ThaiStock;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class StockSummaryService {

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    // Get summary statistics for a stock lot
    public Map<String, Object> getStockLotSummary(Long stockLotId) {
        Map<String, Object> summary = new HashMap<>();

        // China stock summary
        BigDecimal chinaTotalValue = chinaStockRepository.getTotalValueByLot(stockLotId);
        Long chinaItemCount = chinaStockRepository.findByStockLotId(stockLotId).stream().count();
        Integer chinaTotalQuantity = chinaStockRepository.findByStockLotId(stockLotId)
                .stream()
                .mapToInt(stock -> stock.getQuantity() != null ? stock.getQuantity() : 0)
                .sum();

        // Thai stock summary
        BigDecimal thaiTotalValue = thaiStockRepository.getTotalValueByLot(stockLotId);
        Long thaiItemCount = thaiStockRepository.findByStockLotId(stockLotId).stream().count();
        Integer thaiTotalQuantity = thaiStockRepository.findByStockLotId(stockLotId)
                .stream()
                .mapToInt(stock -> stock.getQuantity() != null ? stock.getQuantity() : 0)
                .sum();

        // Overall summary
        summary.put("stockLotId", stockLotId);
        summary.put("chinaTotalValue", chinaTotalValue != null ? chinaTotalValue : BigDecimal.ZERO);
        summary.put("chinaItemCount", chinaItemCount);
        summary.put("chinaTotalQuantity", chinaTotalQuantity);
        summary.put("thaiTotalValue", thaiTotalValue != null ? thaiTotalValue : BigDecimal.ZERO);
        summary.put("thaiItemCount", thaiItemCount);
        summary.put("thaiTotalQuantity", thaiTotalQuantity);

        BigDecimal grandTotal = (chinaTotalValue != null ? chinaTotalValue : BigDecimal.ZERO)
                .add(thaiTotalValue != null ? thaiTotalValue : BigDecimal.ZERO);
        summary.put("grandTotalValue", grandTotal);
        summary.put("totalItemCount", chinaItemCount + thaiItemCount);
        summary.put("totalQuantity", chinaTotalQuantity + thaiTotalQuantity);

        return summary;
    }

    // Get overall system summary
    public Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new HashMap<>();

        // Count totals
        Long totalLots = stockLotRepository.count();
        Long totalChinaItems = chinaStockRepository.count();
        Long totalThaiItems = thaiStockRepository.count();

        // Active counts
        Long activeChinaItems = chinaStockRepository.findByStatus(ChinaStock.StockStatus.ACTIVE).stream().count();
        Long activeThaiItems = thaiStockRepository.findByStatus(ThaiStock.StockStatus.ACTIVE).stream().count();

        summary.put("totalLots", totalLots);
        summary.put("totalChinaItems", totalChinaItems);
        summary.put("totalThaiItems", totalThaiItems);
        summary.put("totalItems", totalChinaItems + totalThaiItems);
        summary.put("activeChinaItems", activeChinaItems);
        summary.put("activeThaiItems", activeThaiItems);
        summary.put("activeItems", activeChinaItems + activeThaiItems);

        return summary;
    }
}