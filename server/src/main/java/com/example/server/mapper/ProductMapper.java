package com.example.server.mapper;

import com.example.server.dto.*;
import com.example.server.entity.*;
import com.example.server.respository.ProductIngredientRepository;
import com.example.server.respository.StockBaseRepository;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProductMapper {

    private final StockBaseRepository stockBaseRepository;
    private final ProductIngredientRepository productIngredientRepository;

    public ProductMapper(StockBaseRepository stockBaseRepository,
                         ProductIngredientRepository productIngredientRepository) {
        this.stockBaseRepository = stockBaseRepository;
        this.productIngredientRepository = productIngredientRepository;
    }

    // Product mappings
    public ProductDTO toProductDTO(Product product) {
        if (product == null) return null;

        ProductDTO dto = new ProductDTO();
        dto.setProductId(product.getProductId());
        dto.setProductName(product.getProductName());
        dto.setDescription(product.getDescription());
        dto.setSku(product.getSku());
        dto.setCategory(product.getCategory());
        dto.setSellingPrice(product.getSellingPrice());
        dto.setCalculatedCost(product.getCalculatedCost());
        dto.setProfitMargin(product.getProfitMargin());
        dto.setStatus(product.getStatus() != null ? product.getStatus().name() : null);

        // ⭐ เพิ่ม: Map imageUrl
        dto.setImageUrl(product.getImageUrl());
        dto.setIsUsingDefaultImage(product.isUsingDefaultImage());

        dto.setCreatedDate(product.getCreatedDate());
        dto.setUpdatedDate(product.getUpdatedDate());

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());
        dto.setIngredients(toProductIngredientDTOList(ingredients));

        return dto;
    }

    public Product toProduct(ProductCreateRequestDTO request) {
        if (request == null) return null;

        Product product = new Product();
        product.setProductName(request.getProductName());
        product.setDescription(request.getDescription());
        product.setSku(request.getSku());
        product.setCategory(request.getCategory());
        product.setSellingPrice(request.getSellingPrice());

        // ⭐ ไม่ต้อง set imageUrl ที่นี่
        // จะถูก set ใน Controller หลังจาก upload ไฟล์แล้ว

        return product;
    }

    public List<ProductDTO> toProductDTOList(List<Product> products) {
        return products.stream()
                .map(this::toProductDTO)
                .collect(Collectors.toList());
    }

    // ProductIngredient mappings
    public ProductIngredientDTO toProductIngredientDTO(ProductIngredient ingredient) {
        if (ingredient == null) return null;

        ProductIngredientDTO dto = new ProductIngredientDTO();
        dto.setIngredientId(ingredient.getIngredientId());
        dto.setProductId(ingredient.getProduct() != null ? ingredient.getProduct().getProductId() : null);
        dto.setStockItemId(ingredient.getStockItem() != null ? ingredient.getStockItem().getStockItemId() : null);
        dto.setIngredientName(ingredient.getIngredientName());
        dto.setRequiredQuantity(ingredient.getRequiredQuantity());
        dto.setUnit(ingredient.getUnit());
        dto.setCostPerUnit(ingredient.getCostPerUnit());
        dto.setTotalCost(ingredient.getTotalCost());
        dto.setNotes(ingredient.getNotes());

        if (ingredient.getStockItem() != null) {
            dto.setStockItemName(ingredient.getStockItem().getName());
            Integer quantity = ingredient.getStockItem().getQuantity();

            if (ingredient.getStockItem() instanceof ChinaStock) {
                dto.setStockType("CHINA");
                dto.setAvailableQuantity(quantity != null ? BigDecimal.valueOf(quantity) : BigDecimal.ZERO);
            } else if (ingredient.getStockItem() instanceof ThaiStock) {
                dto.setStockType("THAI");
                dto.setAvailableQuantity(quantity != null ? BigDecimal.valueOf(quantity) : BigDecimal.ZERO);
            }
        }

        return dto;
    }

    public ProductIngredient toProductIngredient(ProductIngredientRequestDTO request) {
        if (request == null) return null;

        ProductIngredient ingredient = new ProductIngredient();
        ingredient.setIngredientName(request.getIngredientName());
        ingredient.setRequiredQuantity(request.getRequiredQuantity());
        ingredient.setUnit(request.getUnit());
        ingredient.setNotes(request.getNotes());

        if (request.getStockItemId() != null) {
            stockBaseRepository.findById(request.getStockItemId())
                    .ifPresent(ingredient::setStockItem);
        }

        return ingredient;
    }

    public List<ProductIngredientDTO> toProductIngredientDTOList(List<ProductIngredient> ingredients) {
        return ingredients.stream()
                .map(this::toProductIngredientDTO)
                .collect(Collectors.toList());
    }

    public List<ProductIngredient> toProductIngredients(List<ProductIngredientRequestDTO> requests) {
        if (requests == null) return null;
        return requests.stream()
                .map(this::toProductIngredient)
                .collect(Collectors.toList());
    }

    public ProductCostAnalysisDTO toProductCostAnalysisDTO(Product product) {
        if (product == null) return null;

        ProductCostAnalysisDTO dto = new ProductCostAnalysisDTO();
        dto.setProductId(product.getProductId());
        dto.setProductName(product.getProductName());
        dto.setTotalMaterialCost(product.getCalculatedCost() != null ? product.getCalculatedCost() : BigDecimal.ZERO);
        dto.setSellingPrice(product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO);

        BigDecimal grossProfit = dto.getSellingPrice().subtract(dto.getTotalMaterialCost());
        dto.setGrossProfit(grossProfit);

        if (dto.getTotalMaterialCost().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitPercentage = grossProfit
                    .divide(dto.getTotalMaterialCost(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            dto.setProfitMarginPercentage(profitPercentage);
        } else if (dto.getSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
            dto.setProfitMarginPercentage(BigDecimal.valueOf(100));
        } else {
            dto.setProfitMarginPercentage(BigDecimal.ZERO);
        }

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        List<IngredientCostBreakdownDTO> breakdown = ingredients.stream()
                .map(ing -> {
                    IngredientCostBreakdownDTO ingredientDto = toIngredientCostBreakdownDTO(ing);

                    if (dto.getTotalMaterialCost().compareTo(BigDecimal.ZERO) > 0 &&
                            ingredientDto.getTotalCost() != null) {
                        BigDecimal percentage = ingredientDto.getTotalCost()
                                .divide(dto.getTotalMaterialCost(), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                        ingredientDto.setCostPercentage(percentage);
                    } else {
                        ingredientDto.setCostPercentage(BigDecimal.ZERO);
                    }

                    return ingredientDto;
                })
                .collect(Collectors.toList());

        dto.setIngredientBreakdown(breakdown);

        return dto;
    }

    private IngredientCostBreakdownDTO toIngredientCostBreakdownDTO(ProductIngredient ingredient) {
        IngredientCostBreakdownDTO dto = new IngredientCostBreakdownDTO();
        dto.setIngredientName(ingredient.getIngredientName());
        dto.setRequiredQuantity(ingredient.getRequiredQuantity());
        dto.setUnit(ingredient.getUnit());
        dto.setCostPerUnit(ingredient.getCostPerUnit());
        dto.setTotalCost(ingredient.getTotalCost());

        if (ingredient.getStockItem() != null) {
            dto.setStockSource("Stock ID: " + ingredient.getStockItem().getStockItemId() +
                    " - " + ingredient.getStockItem().getName());
        }

        return dto;
    }
}