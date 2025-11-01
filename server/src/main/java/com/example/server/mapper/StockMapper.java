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
        // ลบ totalShippingBath mapping ออกแล้ว
        dto.setStatus(stockLot.getStatus() != null ? stockLot.getStatus().name() : null);

        // Convert items to simple DTOs (without circular reference)
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

        // Determine item type and set basic info
        if (stockBase instanceof ChinaStock) {
            ChinaStock china = (ChinaStock) stockBase;
            dto.setItemType("CHINA");
            dto.setQuantity(china.getQuantity());
            dto.setTotalValue(china.getTotalBath());
            dto.setFinalPrice(china.getFinalPricePerPair());
        } else if (stockBase instanceof ThaiStock) {
            ThaiStock thai = (ThaiStock) stockBase;
            dto.setItemType("THAI");
            dto.setQuantity(thai.getQuantity());
            dto.setTotalValue(thai.calculateTotalCost());
            dto.setFinalPrice(thai.getPricePerUnitWithShipping());
        }

        return dto;
    }

    public ChinaStockDTO toChinaStockDTO(ChinaStock chinaStock) {
        if (chinaStock == null) return null;

        ChinaStockDTO dto = new ChinaStockDTO();
        dto.setStockItemId(chinaStock.getStockItemId());
        dto.setName(chinaStock.getName());
        dto.setLotDate(chinaStock.getLotDate());
        dto.setShopURL(chinaStock.getShopURL());
        dto.setStatus(chinaStock.getStatus() != null ? chinaStock.getStatus().name() : null);

        // China-specific fields
        dto.setUnitPriceYuan(chinaStock.getUnitPriceYuan());
        dto.setQuantity(chinaStock.getQuantity());
        dto.setTotalValueYuan(chinaStock.getTotalValueYuan());
        dto.setShippingWithinChinaYuan(chinaStock.getShippingWithinChinaYuan());
        dto.setTotalYuan(chinaStock.getTotalYuan());
        dto.setTotalBath(chinaStock.getTotalBath());
        dto.setPricePerUnitBath(chinaStock.getPricePerUnitBath());
        dto.setShippingChinaToThaiBath(chinaStock.getShippingChinaToThaiBath());
        dto.setAvgShippingPerPair(chinaStock.getAvgShippingPerPair());
        dto.setFinalPricePerPair(chinaStock.getFinalPricePerPair());
        dto.setExchangeRate(chinaStock.getExchangeRate());

        // StockLot info (เฉพาะ ID, ไม่มี name)
        dto.setStockLotId(chinaStock.getStockLotId());
        dto.setLotName(null); // ไม่ set lotName หรือ set เป็น null

        return dto;
    }

    public ThaiStockDTO toThaiStockDTO(ThaiStock thaiStock) {
        if (thaiStock == null) return null;

        ThaiStockDTO dto = new ThaiStockDTO();
        dto.setStockItemId(thaiStock.getStockItemId());
        dto.setName(thaiStock.getName());
        dto.setLotDate(thaiStock.getLotDate());
        dto.setShopURL(thaiStock.getShopURL());
        dto.setStatus(thaiStock.getStatus() != null ? thaiStock.getStatus().name() : null);

        // Thai-specific fields
        dto.setQuantity(thaiStock.getQuantity());
        dto.setPriceTotal(thaiStock.getPriceTotal());
        dto.setShippingCost(thaiStock.getShippingCost());
        dto.setPricePerUnit(thaiStock.getPricePerUnit());
        dto.setPricePerUnitWithShipping(thaiStock.getPricePerUnitWithShipping());

        // StockLot info (เฉพาะ ID, ไม่มี name)
        dto.setStockLotId(thaiStock.getStockLotId());
        dto.setLotName(null); // ไม่ set lotName หรือ set เป็น null

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