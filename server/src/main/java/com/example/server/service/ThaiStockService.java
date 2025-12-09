package com.example.server.service;

import com.example.server.entity.ThaiStock;
import com.example.server.respository.ThaiStockRepository;
import com.example.server.respository.StockForecastRepository; // ⭐ เพิ่ม
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // ⭐ เพิ่ม
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ThaiStockService {

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    // ⭐ เพิ่ม
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

    /**
     * ⭐ แก้ไข: ลบ forecasts ก่อนลบ stock
     */
    @Transactional
    public void deleteThaiStock(Long id) {
        // 1. ตรวจสอบว่ามี stock อยู่จริง
        ThaiStock stock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));

        try {
            // 2. ลบ forecasts ที่เกี่ยวข้องก่อน
            int deletedForecasts = stockForecastRepository.deleteByStockItemStockItemId(id);
            if (deletedForecasts > 0) {
                System.out.println("🗑️ Deleted " + deletedForecasts + " forecast(s) for Thai Stock ID: " + id);
            }

            // 3. Flush เพื่อให้แน่ใจว่าลบ forecasts แล้ว
            stockForecastRepository.flush();

            // 4. ลบ stock
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
        if (details.getIncludeBuffer() != null) thaiStock.setIncludeBuffer(details.getIncludeBuffer());
        if (details.getBufferPercentage() != null) thaiStock.setBufferPercentage(details.getBufferPercentage());
        if (details.getStatus() != null) thaiStock.setStatus(details.getStatus());
        if (details.getStockLotId() != null) thaiStock.setStockLotId(details.getStockLotId());
    }
}