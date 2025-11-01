package com.example.server.service;

import com.example.server.entity.ChinaStock;
import com.example.server.entity.StockLot;
import com.example.server.respository.ChinaStockRepository;
import com.example.server.respository.StockLotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ChinaStockService {

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    public List<ChinaStock> getAllChinaStocks() {
        return chinaStockRepository.findAll();
    }

    public Optional<ChinaStock> getChinaStockById(Long id) {
        return chinaStockRepository.findById(id);
    }

    public List<ChinaStock> getChinaStocksByStatus(ChinaStock.StockStatus status) {
        return chinaStockRepository.findByStatus(status);
    }

    public List<ChinaStock> getChinaStocksByLot(Long stockLotId) {
        return chinaStockRepository.findByStockLotId(stockLotId);
    }

    public ChinaStock createChinaStock(ChinaStock chinaStock) {
        validateChinaStock(chinaStock);

        // Set default status
        if (chinaStock.getStatus() == null) {
            chinaStock.setStatus(ChinaStock.StockStatus.ACTIVE);
        }

        // Auto-calculations will be handled by @PrePersist
        return chinaStockRepository.save(chinaStock);
    }

    public ChinaStock updateChinaStock(Long id, ChinaStock chinaStockDetails) {
        ChinaStock chinaStock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));

        // Update fields - ใช้ stockLotId แทน stockLot
        updateChinaStockFields(chinaStock, chinaStockDetails);
        validateChinaStock(chinaStock);

        // Auto-calculations will be handled by @PreUpdate
        return chinaStockRepository.save(chinaStock);
    }

    public ChinaStock updateChinaStockStatus(Long id, ChinaStock.StockStatus status) {
        ChinaStock chinaStock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));

        chinaStock.setStatus(status);
        return chinaStockRepository.save(chinaStock);
    }

    public void deleteChinaStock(Long id) {
        chinaStockRepository.deleteById(id);
    }

    public List<ChinaStock> searchChinaStocks(String keyword) {
        return chinaStockRepository.searchByKeyword(keyword);
    }

    public BigDecimal getTotalValueByLot(Long stockLotId) {
        return chinaStockRepository.getTotalValueByLot(stockLotId);
    }

    // Batch update exchange rate for a lot
    public List<ChinaStock> updateExchangeRateForLot(Long stockLotId, BigDecimal exchangeRate) {
        List<ChinaStock> stocks = getChinaStocksByLot(stockLotId);
        stocks.forEach(stock -> {
            stock.setExchangeRate(exchangeRate);
            stock.calculateFields(); // Recalculate derived values
        });
        return chinaStockRepository.saveAll(stocks);
    }

    // แก้ไขการคำนวณ shipping costs - ไม่ต้องใช้ StockLot.totalShippingBath
    public List<ChinaStock> distributeShippingCosts(Long stockLotId) {
        List<ChinaStock> stocks = getChinaStocksByLot(stockLotId);

        // เนื่องจากไม่มี totalShippingBath แล้ว ให้ใช้วิธีอื่น
        // อาจจะรับค่า totalShipping เป็น parameter หรือคำนวณจาก field อื่น

        // สำหรับตอนนี้ ให้ return stocks กลับไปโดยไม่ทำอะไร
        // หรือคำนวณจาก field ที่มีอยู่

        return stocks;
    }

    // เพิ่ม method ใหม่สำหรับ distribute shipping โดยรับ totalShipping เป็น parameter
    public List<ChinaStock> distributeShippingCosts(Long stockLotId, BigDecimal totalShipping) {
        List<ChinaStock> stocks = getChinaStocksByLot(stockLotId);

        if (totalShipping != null && totalShipping.compareTo(BigDecimal.ZERO) > 0) {
            int totalQuantity = stocks.stream().mapToInt(s -> s.getQuantity() != null ? s.getQuantity() : 0).sum();

            if (totalQuantity > 0) {
                stocks.forEach(stock -> {
                    if (stock.getQuantity() != null) {
                        BigDecimal shippingPerPair = totalShipping.multiply(BigDecimal.valueOf(stock.getQuantity()))
                                .divide(BigDecimal.valueOf(totalQuantity), 2, BigDecimal.ROUND_HALF_UP);
                        stock.setAvgShippingPerPair(shippingPerPair.divide(BigDecimal.valueOf(stock.getQuantity()), 2, BigDecimal.ROUND_HALF_UP));
                        stock.calculateFields();
                    }
                });
                return chinaStockRepository.saveAll(stocks);
            }
        }
        return stocks;
    }

    private void validateChinaStock(ChinaStock chinaStock) {
        if (chinaStock.getName() == null || chinaStock.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Stock name is required");
        }
        if (chinaStock.getUnitPriceYuan() == null || chinaStock.getUnitPriceYuan().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unit price must be greater than or equal to 0");
        }
        if (chinaStock.getQuantity() == null || chinaStock.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity must be greater than or equal to 0");
        }
        if (chinaStock.getExchangeRate() == null || chinaStock.getExchangeRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than 0");
        }
    }

    private void updateChinaStockFields(ChinaStock chinaStock, ChinaStock details) {
        if (details.getName() != null) chinaStock.setName(details.getName());
        if (details.getShopURL() != null) chinaStock.setShopURL(details.getShopURL());
        if (details.getUnitPriceYuan() != null) chinaStock.setUnitPriceYuan(details.getUnitPriceYuan());
        if (details.getQuantity() != null) chinaStock.setQuantity(details.getQuantity());
        if (details.getShippingWithinChinaYuan() != null) chinaStock.setShippingWithinChinaYuan(details.getShippingWithinChinaYuan());
        if (details.getExchangeRate() != null) chinaStock.setExchangeRate(details.getExchangeRate());
        if (details.getShippingChinaToThaiBath() != null) chinaStock.setShippingChinaToThaiBath(details.getShippingChinaToThaiBath());
        if (details.getAvgShippingPerPair() != null) chinaStock.setAvgShippingPerPair(details.getAvgShippingPerPair());
        if (details.getStatus() != null) chinaStock.setStatus(details.getStatus());
        if (details.getStockLotId() != null) chinaStock.setStockLotId(details.getStockLotId());
    }
}