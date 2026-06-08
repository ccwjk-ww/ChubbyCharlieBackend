package com.example.server.service;

import com.example.server.entity.ChinaStock;
import com.example.server.respository.ChinaStockRepository;
import com.example.server.respository.StockForecastRepository;
import com.example.server.respository.StockLotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
public class ChinaStockService {

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    @Autowired
    private DefectiveRecordService defectiveRecordService;

    @Autowired
    private StockForecastRepository stockForecastRepository;

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
        if (chinaStock.getStatus() == null) {
            chinaStock.setStatus(ChinaStock.StockStatus.ACTIVE);
        }
        return chinaStockRepository.save(chinaStock);
    }

    public ChinaStock updateChinaStock(Long id, ChinaStock chinaStockDetails) {
        ChinaStock chinaStock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));
        updateChinaStockFields(chinaStock, chinaStockDetails);
        validateChinaStock(chinaStock);
        return chinaStockRepository.save(chinaStock);
    }

    public ChinaStock updateChinaStockStatus(Long id, ChinaStock.StockStatus status) {
        ChinaStock chinaStock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));
        chinaStock.setStatus(status);
        return chinaStockRepository.save(chinaStock);
    }

    // ============================================
    // ⭐ NEW: Record Defective Quantity
    // ============================================

    /**
     * บันทึกจำนวนของเสีย
     * @param id    stockItemId
     * @param count จำนวนของเสียที่ต้องการเพิ่ม (ต้องมากกว่า 0)
     */

    @Transactional
    public ChinaStock recordDefective(Long id, int count, String note) {
        if (count <= 0) {
            throw new IllegalArgumentException("Defective count must be greater than 0");
        }

        ChinaStock stock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));

        // ⭐ ตรวจสอบว่า quantity พอ
        int currentQty = stock.getCurrentQuantity();
        if (count > currentQty) {
            throw new IllegalArgumentException(
                    "ของเสียเกินจำนวนคงเหลือ: คงเหลือ " + currentQty + " ชิ้น แต่ระบุ " + count + " ชิ้น");
        }

        // 1. เพิ่ม defectiveQuantity (สะสม)
        int currentDefective = stock.getDefectiveQuantity() != null ? stock.getDefectiveQuantity() : 0;
        stock.setDefectiveQuantity(currentDefective + count);

        // 2. ⭐ ตัดออกจาก quantity
        stock.setQuantity(currentQty - count);

        // 3. บันทึก history
        BigDecimal unitCost = stock.getAverageCostPerUnitWithVat();
        defectiveRecordService.createRecord(id, count, unitCost, "CHINA", note);

        System.out.println("📦 Recorded " + count + " defective for China Stock ID: " + id
                + " | qty: " + currentQty + " → " + stock.getQuantity()
                + " | Total defective: " + stock.getDefectiveQuantity());

        return chinaStockRepository.save(stock);
    }
    /**
     * รีเซ็ตจำนวนของเสียเป็น 0 หรือค่าที่กำหนด
     */
    @Transactional
    public ChinaStock setDefectiveQuantity(Long id, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Defective count cannot be negative");
        }

        ChinaStock stock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));

        stock.setDefectiveQuantity(count);
        return chinaStockRepository.save(stock);
    }

    // ============================================
    // ไม่เปลี่ยน methods เดิม
    // ============================================

    @Transactional
    public void deleteChinaStock(Long id) {
        ChinaStock stock = chinaStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("China stock not found with id: " + id));
        try {
            int deletedForecasts = stockForecastRepository.deleteByStockItemStockItemId(id);
            if (deletedForecasts > 0) {
                System.out.println("🗑️ Deleted " + deletedForecasts + " forecast(s) for China Stock ID: " + id);
            }
            stockForecastRepository.flush();
            chinaStockRepository.delete(stock);
            chinaStockRepository.flush();
            System.out.println("✅ Successfully deleted China Stock ID: " + id);
        } catch (Exception e) {
            System.err.println("❌ Error deleting China Stock ID " + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to delete China stock: " + e.getMessage(), e);
        }
    }

    public List<ChinaStock> searchChinaStocks(String keyword) {
        return chinaStockRepository.searchByKeyword(keyword);
    }

    public BigDecimal getTotalValueByLot(Long stockLotId) {
        List<ChinaStock> stocks = getChinaStocksByLot(stockLotId);
        return stocks.stream()
                .map(ChinaStock::calculateTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ChinaStock> updateExchangeRateForLot(Long stockLotId, BigDecimal exchangeRate) {
        List<ChinaStock> stocks = getChinaStocksByLot(stockLotId);
        stocks.forEach(stock -> {
            stock.setExchangeRate(exchangeRate);
            stock.calculateFields();
        });
        return chinaStockRepository.saveAll(stocks);
    }

    // ✅ โค้ดที่ถูกต้อง — ใช้ originalQuantity
    public List<ChinaStock> distributeShippingCosts(Long stockLotId, BigDecimal totalShipping) {
        List<ChinaStock> stocks = getChinaStocksByLot(stockLotId);
        if (totalShipping != null && totalShipping.compareTo(BigDecimal.ZERO) > 0) {

            // ⭐ ใช้ originalQuantity เพื่อคำนวณสัดส่วน shipping ที่ถูกต้อง
            int totalQuantity = stocks.stream()
                    .mapToInt(s -> s.getOriginalQuantity() != null && s.getOriginalQuantity() > 0
                            ? s.getOriginalQuantity()
                            : (s.getQuantity() != null ? s.getQuantity() : 0))
                    .sum();

            if (totalQuantity > 0) {
                stocks.forEach(stock -> {
                    // ⭐ ใช้ originalQuantity ในการคำนวณสัดส่วน
                    int qty = (stock.getOriginalQuantity() != null && stock.getOriginalQuantity() > 0)
                            ? stock.getOriginalQuantity()
                            : (stock.getQuantity() != null ? stock.getQuantity() : 0);

                    if (qty > 0) {
                        BigDecimal stockShippingPortion = totalShipping
                                .multiply(BigDecimal.valueOf(qty))
                                .divide(BigDecimal.valueOf(totalQuantity), 3, RoundingMode.HALF_UP);
                        stock.setShippingChinaToThaiBath(stockShippingPortion);

                        // ⭐ reset unitCostAtImport และ totalCostAtImport
                        // เพื่อให้ calculateFields() คำนวณใหม่จาก totalBath ที่อัพเดทแล้ว
                        stock.setUnitCostAtImport(null);
                        stock.setTotalCostAtImport(null);

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
        if (details.getIncludeVat() != null) chinaStock.setIncludeVat(details.getIncludeVat());
        if (details.getVatPercentage() != null) chinaStock.setVatPercentage(details.getVatPercentage());
        if (details.getStatus() != null) chinaStock.setStatus(details.getStatus());
        if (details.getStockLotId() != null) chinaStock.setStockLotId(details.getStockLotId());
        // ⭐ Allow updating defectiveQuantity via update endpoint as well
        if (details.getDefectiveQuantity() != null) chinaStock.setDefectiveQuantity(details.getDefectiveQuantity());
    }
}