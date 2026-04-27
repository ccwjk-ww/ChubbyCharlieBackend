package com.example.server.mapper;

import com.example.server.dto.*;
import com.example.server.entity.*;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
            dto.setQuantity(china.getCurrentQuantity());

            dto.setIncludeVat(china.getIncludeVat());
            dto.setVatPercentage(china.getVatPercentage());

            BigDecimal totalBath = china.getTotalBath() != null
                    ? china.getTotalBath() : BigDecimal.ZERO;

            dto.setTotalBath(totalBath);
            dto.setTotalValueBeforeVat(totalBath);
            dto.setTotalValue(totalBath);
            dto.setFinalPrice(china.getAverageCostPerUnit()); // ⭐ แก้: ใช้ getAverageCostPerUnit()
            dto.setFinalPricePerPair(china.getAverageCostPerUnit()); // ⭐ แก้
            dto.setShippingChinaToThaiBath(china.getShippingChinaToThaiBath());

            BigDecimal vatAmount = calculateVatAmount(totalBath, china.getIncludeVat(), china.getVatPercentage());
            BigDecimal totalWithVat = totalBath.add(vatAmount);
            BigDecimal finalWithVat = china.getCurrentQuantity() != null && china.getCurrentQuantity() > 0
                    ? totalWithVat.divide(BigDecimal.valueOf(china.getCurrentQuantity()), 3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            dto.setVatAmount(vatAmount);
            dto.setTotalValueWithVat(totalWithVat);
            dto.setFinalPriceWithVat(finalWithVat);

            dto.setDefectiveQuantity(china.getDefectiveQuantity());
            dto.setDefectiveValue(china.getDefectiveValue());

        } else if (stockBase instanceof ThaiStock) {
            ThaiStock thai = (ThaiStock) stockBase;
            dto.setItemType("THAI");
            dto.setQuantity(thai.getCurrentQuantity());

            dto.setIncludeVat(thai.getIncludeVat());
            dto.setVatPercentage(thai.getVatPercentage());

            BigDecimal priceTotal = thai.getPriceTotal() != null ? thai.getPriceTotal() : BigDecimal.ZERO;
            BigDecimal shipping = thai.getShippingCost() != null ? thai.getShippingCost() : BigDecimal.ZERO;
            BigDecimal totalBeforeVat = priceTotal.add(shipping);

            dto.setTotalValueBeforeVat(totalBeforeVat);
            dto.setTotalValue(totalBeforeVat);
            dto.setFinalPrice(thai.getAverageCostPerUnit()); // ⭐ แก้: ใช้ getAverageCostPerUnit()

            BigDecimal vatAmount = calculateVatAmount(totalBeforeVat, thai.getIncludeVat(), thai.getVatPercentage());
            BigDecimal totalWithVat = totalBeforeVat.add(vatAmount);
            BigDecimal finalWithVat = thai.getCurrentQuantity() != null && thai.getCurrentQuantity() > 0
                    ? totalWithVat.divide(BigDecimal.valueOf(thai.getCurrentQuantity()), 3, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            dto.setVatAmount(vatAmount);
            dto.setTotalValueWithVat(totalWithVat);
            dto.setFinalPriceWithVat(finalWithVat);

            dto.setDefectiveQuantity(thai.getDefectiveQuantity());
            dto.setDefectiveValue(thai.getDefectiveValue());
        }

        return dto;
    }

    private BigDecimal calculateVatAmount(BigDecimal base, Boolean includeVat, BigDecimal vatPercentage) {
        if (Boolean.TRUE.equals(includeVat)
                && vatPercentage != null
                && vatPercentage.compareTo(BigDecimal.ZERO) > 0
                && base != null) {
            return base.multiply(vatPercentage)
                    .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    // ============================================
    // ChinaStock mapper
    // ============================================

    public ChinaStockDTO toChinaStockDTO(ChinaStock chinaStock) {
        if (chinaStock == null) return null;

        ChinaStockDTO dto = new ChinaStockDTO();
        dto.setStockItemId(chinaStock.getStockItemId());
        dto.setName(chinaStock.getName());
        dto.setLotDate(chinaStock.getLotDate());
        dto.setShopURL(chinaStock.getShopURL());
        dto.setStatus(chinaStock.getStatus() != null ? chinaStock.getStatus().name() : null);

        dto.setOriginalQuantity(chinaStock.getOriginalQuantity());
        dto.setCurrentQuantity(chinaStock.getCurrentQuantity());
        dto.setUsedQuantity(chinaStock.getUsedQuantity());
        dto.setUsagePercentage(chinaStock.getUsagePercentage());
        dto.setRemainingPercentage(chinaStock.getRemainingPercentage());
        dto.setQuantity(chinaStock.getCurrentQuantity());

        dto.setDefectiveQuantity(chinaStock.getDefectiveQuantity());
        dto.setDefectiveValue(chinaStock.getDefectiveValue());

        dto.setUnitPriceYuan(chinaStock.getUnitPriceYuan());
        dto.setTotalValueYuan(chinaStock.getTotalValueYuan());
        dto.setShippingWithinChinaYuan(chinaStock.getShippingWithinChinaYuan());
        dto.setTotalYuan(chinaStock.getTotalYuan());
        dto.setTotalBath(chinaStock.getTotalBath());
        dto.setPricePerUnitBath(chinaStock.getPricePerUnitBath());
        dto.setShippingChinaToThaiBath(chinaStock.getShippingChinaToThaiBath());

        // ⭐ แก้: ใช้ getAverageCostPerUnit() เพื่อให้ได้ราคาต่อหน่วยก่อน VAT ที่ถูกต้อง
        dto.setFinalPricePerPair(chinaStock.getAverageCostPerUnit());
        dto.setExchangeRate(chinaStock.getExchangeRate());

        dto.setIncludeVat(chinaStock.getIncludeVat());
        dto.setVatPercentage(chinaStock.getVatPercentage());

        dto.setStockLotId(chinaStock.getStockLotId());
        dto.setLotName(null);

        return dto;
    }

    // ============================================
    // ThaiStock mapper
    // ============================================

    public ThaiStockDTO toThaiStockDTO(ThaiStock thaiStock) {
        if (thaiStock == null) return null;

        ThaiStockDTO dto = new ThaiStockDTO();
        dto.setStockItemId(thaiStock.getStockItemId());
        dto.setName(thaiStock.getName());
        dto.setLotDate(thaiStock.getLotDate());
        dto.setShopURL(thaiStock.getShopURL());
        dto.setStatus(thaiStock.getStatus() != null ? thaiStock.getStatus().name() : null);

        dto.setOriginalQuantity(thaiStock.getOriginalQuantity());
        dto.setCurrentQuantity(thaiStock.getCurrentQuantity());
        dto.setUsedQuantity(thaiStock.getUsedQuantity());
        dto.setUsagePercentage(thaiStock.getUsagePercentage());
        dto.setRemainingPercentage(thaiStock.getRemainingPercentage());
        dto.setQuantity(thaiStock.getCurrentQuantity());

        dto.setDefectiveQuantity(thaiStock.getDefectiveQuantity());
        dto.setDefectiveValue(thaiStock.getDefectiveValue());

        dto.setPriceTotal(thaiStock.getPriceTotal());
        dto.setShippingCost(thaiStock.getShippingCost());
        dto.setPricePerUnit(thaiStock.getPricePerUnit());

        // ⭐ แก้: ใช้ getAverageCostPerUnit() เพื่อให้ได้ราคาต่อหน่วยก่อน VAT ที่ถูกต้อง
        dto.setPricePerUnitWithShipping(thaiStock.getAverageCostPerUnit());
        dto.setTotalCost(thaiStock.calculateTotalCost());

        dto.setIncludeVat(thaiStock.getIncludeVat());
        dto.setVatPercentage(thaiStock.getVatPercentage());

        dto.setStockLotId(thaiStock.getStockLotId());
        dto.setLotName(null);

        return dto;
    }

    public List<ChinaStockDTO> toChinaStockDTOList(List<ChinaStock> stocks) {
        return stocks.stream().map(this::toChinaStockDTO).collect(Collectors.toList());
    }

    public List<ThaiStockDTO> toThaiStockDTOList(List<ThaiStock> stocks) {
        return stocks.stream().map(this::toThaiStockDTO).collect(Collectors.toList());
    }

    public List<StockLotDTO> toStockLotDTOList(List<StockLot> stockLots) {
        return stockLots.stream().map(this::toStockLotDTO).collect(Collectors.toList());
    }
}