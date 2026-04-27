package com.example.server.service;

import com.example.server.entity.ThaiStock;
import com.example.server.respository.StockForecastRepository;
import com.example.server.respository.ThaiStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ThaiStockService {

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    @Autowired
    private DefectiveRecordService defectiveRecordService;

    @Autowired
    private StockForecastRepository stockForecastRepository;

    public List<ThaiStock> getAllThaiStocks() {
        return thaiStockRepository.findAll();
    }

    public Optional<ThaiStock> getThaiStockById(Long id) {
        return thaiStockRepository.findById(id);
    }

    public List<ThaiStock> getThaiStocksByStatus(ThaiStock.StockStatus status) {
        return thaiStockRepository.findByStatus(status);
    }

    public List<ThaiStock> getThaiStocksByLot(Long stockLotId) {
        return thaiStockRepository.findByStockLotId(stockLotId);
    }

    public ThaiStock createThaiStock(ThaiStock thaiStock) {
        validateThaiStock(thaiStock);
        if (thaiStock.getStatus() == null) {
            thaiStock.setStatus(ThaiStock.StockStatus.ACTIVE);
        }
        return thaiStockRepository.save(thaiStock);
    }

    public ThaiStock updateThaiStock(Long id, ThaiStock thaiStockDetails) {
        ThaiStock thaiStock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));
        updateThaiStockFields(thaiStock, thaiStockDetails);
        validateThaiStock(thaiStock);
        return thaiStockRepository.save(thaiStock);
    }

    public ThaiStock updateThaiStockStatus(Long id, ThaiStock.StockStatus status) {
        ThaiStock thaiStock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));
        thaiStock.setStatus(status);
        return thaiStockRepository.save(thaiStock);
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
    public ThaiStock recordDefective(Long id, int count, String note) {
        if (count <= 0) throw new IllegalArgumentException("Defective count must be greater than 0");

        ThaiStock stock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));

        int currentQty = stock.getCurrentQuantity();
        if (count > currentQty) {
            throw new IllegalArgumentException(
                    "ของเสียเกินจำนวนคงเหลือ: คงเหลือ " + currentQty + " ชิ้น แต่ระบุ " + count + " ชิ้น");
        }

        stock.setDefectiveQuantity((stock.getDefectiveQuantity() != null ? stock.getDefectiveQuantity() : 0) + count);
        stock.setQuantity(currentQty - count);  // ⭐ ตัดสต็อก

        defectiveRecordService.createRecord(id, count, stock.getAverageCostPerUnitWithVat(), "THAI", note);

        return thaiStockRepository.save(stock);
    }

    /**
     * รีเซ็ตจำนวนของเสียเป็น 0 หรือค่าที่กำหนด
     */
    @Transactional
    public ThaiStock setDefectiveQuantity(Long id, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Defective count cannot be negative");
        }

        ThaiStock stock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));

        stock.setDefectiveQuantity(count);
        return thaiStockRepository.save(stock);
    }

    // ============================================
    // ไม่เปลี่ยน methods เดิม
    // ============================================

    @Transactional
    public void deleteThaiStock(Long id) {
        ThaiStock stock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));
        try {
            int deletedForecasts = stockForecastRepository.deleteByStockItemStockItemId(id);
            if (deletedForecasts > 0) {
                System.out.println("🗑️ Deleted " + deletedForecasts + " forecast(s) for Thai Stock ID: " + id);
            }
            stockForecastRepository.flush();
            thaiStockRepository.delete(stock);
            thaiStockRepository.flush();
            System.out.println("✅ Successfully deleted Thai Stock ID: " + id);
        } catch (Exception e) {
            System.err.println("❌ Error deleting Thai Stock ID " + id + ": " + e.getMessage());
            throw new RuntimeException("Failed to delete Thai stock: " + e.getMessage(), e);
        }
    }

    public List<ThaiStock> searchThaiStocks(String keyword) {
        return thaiStockRepository.searchByKeyword(keyword);
    }

    public BigDecimal getTotalValueByLot(Long stockLotId) {
        List<ThaiStock> stocks = getThaiStocksByLot(stockLotId);
        return stocks.stream()
                .map(ThaiStock::calculateTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateThaiStock(ThaiStock thaiStock) {
        if (thaiStock.getName() == null || thaiStock.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Stock name is required");
        }
        if (thaiStock.getPriceTotal() == null || thaiStock.getPriceTotal().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price total must be greater than or equal to 0");
        }
        if (thaiStock.getQuantity() == null || thaiStock.getQuantity() < 0) {
            throw new IllegalArgumentException("Quantity must be greater than or equal to 0");
        }
    }

    private void updateThaiStockFields(ThaiStock thaiStock, ThaiStock details) {
        if (details.getName() != null) thaiStock.setName(details.getName());
        if (details.getShopURL() != null) thaiStock.setShopURL(details.getShopURL());
        if (details.getQuantity() != null) thaiStock.setQuantity(details.getQuantity());
        if (details.getPriceTotal() != null) thaiStock.setPriceTotal(details.getPriceTotal());
        if (details.getShippingCost() != null) thaiStock.setShippingCost(details.getShippingCost());
        if (details.getIncludeVat() != null) thaiStock.setIncludeVat(details.getIncludeVat());
        if (details.getVatPercentage() != null) thaiStock.setVatPercentage(details.getVatPercentage());
        if (details.getStatus() != null) thaiStock.setStatus(details.getStatus());
        if (details.getStockLotId() != null) thaiStock.setStockLotId(details.getStockLotId());
        // ⭐ Allow updating defectiveQuantity via update endpoint as well
        if (details.getDefectiveQuantity() != null) thaiStock.setDefectiveQuantity(details.getDefectiveQuantity());
    }

}