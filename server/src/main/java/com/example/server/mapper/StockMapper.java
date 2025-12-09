package com.example.server.mapper;

import com.example.server.dto.*;
import com.example.server.entity.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class StockMapper {

    public StockLotDTO toStockLotDTO(StockLot stockLot) {
        if (stockLot == null) return null;

        StockLotDTO dto = new StockLotDTO();
        dto.setStockLotId(stockLot.getStockLotId());
        dto.setLotName(stockLot.getLotName());
        dto.setImportDate(stockLot.getImportDate());
        dto.setArrivalDate(stockLot.getArrivalDate());
        dto.setStatus(stockLot.getStatus() != null ? stockLot.getStatus().name() : null);

        // Convert items to simple DTOs
        if (stockLot.getItems() != null) {
            dto.setItems(stockLot.getItems().stream()
                    .map(this::toStockItemDTO)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private StockLotDTO.StockItemDTO toStockItemDTO(StockBase stockBase) {
        if (stockBase == null) return null;

        StockLotDTO.StockItemDTO dto = new StockLotDTO.StockItemDTO();
        dto.setStockItemId(stockBase.getStockItemId());
        dto.setName(stockBase.getName());
        dto.setLotDate(stockBase.getLotDate());
        dto.setShopURL(stockBase.getShopURL());
        dto.setStatus(stockBase.getStatus() != null ? stockBase.getStatus().name() : null);

        if (stockBase instanceof ChinaStock) {
            ChinaStock china = (ChinaStock) stockBase;
            dto.setItemType("CHINA");
            dto.setQuantity(china.getCurrentQuantity()); // ⭐ ใช้ currentQuantity

            dto.setTotalValue(china.calculateTotalCost());
            dto.setFinalPrice(china.calculateFinalPrice());

        } else if (stockBase instanceof ThaiStock) {
            ThaiStock thai = (ThaiStock) stockBase;
            dto.setItemType("THAI");
            dto.setQuantity(thai.getCurrentQuantity()); // ⭐ ใช้ currentQuantity

            dto.setTotalValue(thai.calculateTotalCost());
            dto.setFinalPrice(thai.calculateFinalPrice());
        }

        return dto;
    }

    /**
     * ⭐ อัปเดต: Map ChinaStock พร้อม quantity tracking
     */
    public ChinaStockDTO toChinaStockDTO(ChinaStock chinaStock) {
        if (chinaStock == null) return null;

        ChinaStockDTO dto = new ChinaStockDTO();
        dto.setStockItemId(chinaStock.getStockItemId());
        dto.setName(chinaStock.getName());
        dto.setLotDate(chinaStock.getLotDate());
        dto.setShopURL(chinaStock.getShopURL());
        dto.setStatus(chinaStock.getStatus() != null ? chinaStock.getStatus().name() : null);

        // ⭐ Quantity Management
        dto.setOriginalQuantity(chinaStock.getOriginalQuantity());
        dto.setCurrentQuantity(chinaStock.getCurrentQuantity());
        dto.setUsedQuantity(chinaStock.getUsedQuantity());
        dto.setUsagePercentage(chinaStock.getUsagePercentage());
        dto.setRemainingPercentage(chinaStock.getRemainingPercentage());

        // ⭐ Backward compatibility
        dto.setQuantity(chinaStock.getCurrentQuantity());

        // Price fields
        dto.setUnitPriceYuan(chinaStock.getUnitPriceYuan());
        dto.setTotalValueYuan(chinaStock.getTotalValueYuan());
        dto.setShippingWithinChinaYuan(chinaStock.getShippingWithinChinaYuan());
        dto.setTotalYuan(chinaStock.getTotalYuan());
        dto.setTotalBath(chinaStock.calculateTotalCost());
        dto.setPricePerUnitBath(chinaStock.getPricePerUnitBath());
        dto.setShippingChinaToThaiBath(chinaStock.getShippingChinaToThaiBath());
        dto.setFinalPricePerPair(chinaStock.calculateFinalPrice());
        dto.setExchangeRate(chinaStock.getExchangeRate());
        dto.setIncludeBuffer(chinaStock.getIncludeBuffer());
        dto.setBufferPercentage(chinaStock.getBufferPercentage());

        dto.setStockLotId(chinaStock.getStockLotId());
        dto.setLotName(null);

        return dto;
    }

    /**
     * ⭐ อัปเดต: Map ThaiStock พร้อม quantity tracking
     */
    public ThaiStockDTO toThaiStockDTO(ThaiStock thaiStock) {
        if (thaiStock == null) return null;

        ThaiStockDTO dto = new ThaiStockDTO();
        dto.setStockItemId(thaiStock.getStockItemId());
        dto.setName(thaiStock.getName());
        dto.setLotDate(thaiStock.getLotDate());
        dto.setShopURL(thaiStock.getShopURL());
        dto.setStatus(thaiStock.getStatus() != null ? thaiStock.getStatus().name() : null);

        // ⭐ Quantity Management
        dto.setOriginalQuantity(thaiStock.getOriginalQuantity());
        dto.setCurrentQuantity(thaiStock.getCurrentQuantity());
        dto.setUsedQuantity(thaiStock.getUsedQuantity());
        dto.setUsagePercentage(thaiStock.getUsagePercentage());
        dto.setRemainingPercentage(thaiStock.getRemainingPercentage());

        // ⭐ Backward compatibility
        dto.setQuantity(thaiStock.getCurrentQuantity());

        // Price fields
        dto.setPriceTotal(thaiStock.getPriceTotal());
        dto.setShippingCost(thaiStock.getShippingCost());
        dto.setPricePerUnit(thaiStock.getPricePerUnit());
        dto.setPricePerUnitWithShipping(thaiStock.getPricePerUnitWithShipping());
        dto.setTotalCost(thaiStock.calculateTotalCost());
        dto.setIncludeBuffer(thaiStock.getIncludeBuffer());
        dto.setBufferPercentage(thaiStock.getBufferPercentage());

        dto.setStockLotId(thaiStock.getStockLotId());
        dto.setLotName(null);

        return dto;
    }

    public List<ChinaStockDTO> toChinaStockDTOList(List<ChinaStock> stocks) {
        return stocks.stream()
                .map(this::toChinaStockDTO)
                .collect(Collectors.toList());
    }

    public List<ThaiStockDTO> toThaiStockDTOList(List<ThaiStock> stocks) {
        return stocks.stream()
                .map(this::toThaiStockDTO)
                .collect(Collectors.toList());
    }

    public List<StockLotDTO> toStockLotDTOList(List<StockLot> stockLots) {
        return stockLots.stream()
                .map(this::toStockLotDTO)
                .collect(Collectors.toList());
    }
}