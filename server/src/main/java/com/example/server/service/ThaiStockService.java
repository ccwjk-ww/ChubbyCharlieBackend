package com.example.server.service;

import com.example.server.entity.ThaiStock;
import com.example.server.respository.ThaiStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class ThaiStockService {

    @Autowired
    private ThaiStockRepository thaiStockRepository;

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

        // Set default status
        if (thaiStock.getStatus() == null) {
            thaiStock.setStatus(ThaiStock.StockStatus.ACTIVE);
        }

        // Auto-calculations will be handled by @PrePersist
        return thaiStockRepository.save(thaiStock);
    }

    public ThaiStock updateThaiStock(Long id, ThaiStock thaiStockDetails) {
        ThaiStock thaiStock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));

        // Update fields
        updateThaiStockFields(thaiStock, thaiStockDetails);
        validateThaiStock(thaiStock);

        // Auto-calculations will be handled by @PreUpdate
        return thaiStockRepository.save(thaiStock);
    }

    public ThaiStock updateThaiStockStatus(Long id, ThaiStock.StockStatus status) {
        ThaiStock thaiStock = thaiStockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Thai stock not found with id: " + id));

        thaiStock.setStatus(status);
        return thaiStockRepository.save(thaiStock);
    }

    public void deleteThaiStock(Long id) {
        thaiStockRepository.deleteById(id);
    }

    public List<ThaiStock> searchThaiStocks(String keyword) {
        return thaiStockRepository.searchByKeyword(keyword);
    }

    public BigDecimal getTotalValueByLot(Long stockLotId) {
        return thaiStockRepository.getTotalValueByLot(stockLotId);
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
        if (thaiStock.getShippingCost() == null) {
            thaiStock.setShippingCost(BigDecimal.ZERO);
        }
    }

    private void updateThaiStockFields(ThaiStock thaiStock, ThaiStock details) {
        if (details.getName() != null) thaiStock.setName(details.getName());
        if (details.getShopURL() != null) thaiStock.setShopURL(details.getShopURL());
        if (details.getQuantity() != null) thaiStock.setQuantity(details.getQuantity());
        if (details.getPriceTotal() != null) thaiStock.setPriceTotal(details.getPriceTotal());
        if (details.getShippingCost() != null) thaiStock.setShippingCost(details.getShippingCost());

        // ⭐ เพิ่ม buffer fields
        if (details.getIncludeBuffer() != null) thaiStock.setIncludeBuffer(details.getIncludeBuffer());
        if (details.getBufferPercentage() != null) thaiStock.setBufferPercentage(details.getBufferPercentage());

        if (details.getStatus() != null) thaiStock.setStatus(details.getStatus());
        if (details.getStockLotId() != null) thaiStock.setStockLotId(details.getStockLotId());
    }
}