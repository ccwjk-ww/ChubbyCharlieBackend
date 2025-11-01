package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.ProductIngredientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@Transactional
public class ProductCostCalculationService {

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    /**
     * ✅ คำนวณต้นทุนของ Product Ingredient โดยดึงราคาจาก Stock
     */
    public BigDecimal calculateIngredientCost(ProductIngredient ingredient) {
        if (ingredient == null) {
            return BigDecimal.ZERO;
        }

        if (ingredient.getStockItem() == null) {
            // ถ้าไม่มี Stock Item ให้ส่ง ZERO กลับ
            ingredient.setCostPerUnit(BigDecimal.ZERO);
            ingredient.setTotalCost(BigDecimal.ZERO);
            return BigDecimal.ZERO;
        }

        // ✅ ดึงราคาต่อหน่วยจาก Stock Item
        BigDecimal stockUnitCost = getStockItemUnitCost(ingredient.getStockItem());

        // ✅ ตรวจสอบค่า null
        if (stockUnitCost == null) {
            stockUnitCost = BigDecimal.ZERO;
        }

        // ตั้งต้นทุนต่อหน่วย
        ingredient.setCostPerUnit(stockUnitCost);

        // ✅ คำนวณต้นทุนรวม = ราคาต่อหน่วย × จำนวนที่ต้องใช้
        BigDecimal requiredQty = ingredient.getRequiredQuantity() != null
                ? ingredient.getRequiredQuantity()
                : BigDecimal.ZERO;

        BigDecimal totalCost = stockUnitCost.multiply(requiredQty)
                .setScale(2, RoundingMode.HALF_UP);

        ingredient.setTotalCost(totalCost);

        return totalCost;
    }

    /**
     * ✅ แก้ไข: ดึงราคาต่อหน่วยจาก Stock Item โดยใช้ method ที่มีอยู่แล้ว
     */
    private BigDecimal getStockItemUnitCost(StockBase stockItem) {
        if (stockItem == null) {
            return BigDecimal.ZERO;
        }

        try {
            if (stockItem instanceof ChinaStock) {
                ChinaStock chinaStock = (ChinaStock) stockItem;

                // ✅ ใช้ finalPricePerPair (ราคาสุดท้ายต่อคู่)
                BigDecimal finalPrice = chinaStock.getFinalPricePerPair();

                if (finalPrice == null || finalPrice.compareTo(BigDecimal.ZERO) == 0) {
                    // ถ้ายังไม่คำนวณ ให้คำนวณใหม่
                    finalPrice = chinaStock.calculateFinalPrice();
                }

                return finalPrice != null ? finalPrice : BigDecimal.ZERO;

            } else if (stockItem instanceof ThaiStock) {
                ThaiStock thaiStock = (ThaiStock) stockItem;

                // ✅ ใช้ pricePerUnitWithShipping (ราคาต่อหน่วยรวมค่าส่ง)
                BigDecimal priceWithShipping = thaiStock.getPricePerUnitWithShipping();

                if (priceWithShipping == null || priceWithShipping.compareTo(BigDecimal.ZERO) == 0) {
                    // ถ้ายังไม่คำนวณ ให้คำนวณใหม่
                    priceWithShipping = thaiStock.calculateFinalPrice();
                }

                return priceWithShipping != null ? priceWithShipping : BigDecimal.ZERO;
            }
        } catch (Exception e) {
            // Log error แต่ไม่ throw exception
            System.err.println("Error calculating stock unit cost: " + e.getMessage());
            return BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }

    /**
     * ✅ คำนวณต้นทุนรวมของ Product
     */
    public BigDecimal calculateProductTotalCost(Product product) {
        if (product == null) {
            return BigDecimal.ZERO;
        }

        // ✅ ดึง Ingredients ทั้งหมดของ Product
        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            // ถ้าไม่มี Ingredients ให้ตั้งต้นทุนเป็น 0
            product.setCalculatedCost(BigDecimal.ZERO);
            product.setProfitMargin(product.getSellingPrice());
            return BigDecimal.ZERO;
        }

        // ✅ รวมต้นทุนจากทุก Ingredient
        BigDecimal totalCost = BigDecimal.ZERO;

        for (ProductIngredient ingredient : ingredients) {
            BigDecimal ingredientCost = calculateIngredientCost(ingredient);
            totalCost = totalCost.add(ingredientCost);
        }

        // ✅ ปัดเศษให้เป็น 2 ตำแหน่ง
        totalCost = totalCost.setScale(2, RoundingMode.HALF_UP);

        // ✅ บันทึกต้นทุนรวมใน Product
        product.setCalculatedCost(totalCost);

        // ✅ คำนวณกำไรและเปอร์เซ็นต์
        if (product.getSellingPrice() != null) {
            BigDecimal sellingPrice = product.getSellingPrice();
            BigDecimal profit = sellingPrice.subtract(totalCost);

            product.setProfitMargin(profit);

            // ✅ คำนวณเปอร์เซ็นต์กำไร (ถ้าต้องการ)
            // profitPercentage = (profit / cost) × 100
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitPercentage = profit
                        .divide(totalCost, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                // ถ้ามี field profitPercentage ใน Product entity
                // product.setProfitPercentage(profitPercentage);
            }
        } else {
            // ถ้าไม่มีราคาขาย กำไร = 0
            product.setProfitMargin(BigDecimal.ZERO);
        }

        return totalCost;
    }

    /**
     * ✅ เพิ่ม: คำนวณต้นทุนสำหรับหลาย Products พร้อมกัน
     */
    @Transactional
    public void recalculateAllProductCosts(List<Product> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        for (Product product : products) {
            try {
                calculateProductTotalCost(product);
            } catch (Exception e) {
                System.err.println("Error calculating cost for product " +
                        product.getProductId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * ✅ เพิ่ม: ตรวจสอบว่า Product มี Ingredients หรือไม่
     */
    public boolean hasIngredients(Product product) {
        if (product == null) {
            return false;
        }

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        return ingredients != null && !ingredients.isEmpty();
    }

    /**
     * ✅ เพิ่ม: คำนวณกำไรเป็นเปอร์เซ็นต์
     */
    public BigDecimal calculateProfitPercentage(Product product) {
        if (product == null ||
                product.getCalculatedCost() == null ||
                product.getSellingPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal cost = product.getCalculatedCost();
        BigDecimal price = product.getSellingPrice();

        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal profit = price.subtract(cost);

        // Profit Percentage = (Profit / Cost) × 100
        return profit
                .divide(cost, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * ✅ เพิ่ม: คำนวณ Markup Percentage
     * Markup = ((Price - Cost) / Cost) × 100
     */
    public BigDecimal calculateMarkupPercentage(Product product) {
        return calculateProfitPercentage(product);
    }

    /**
     * ✅ เพิ่ม: คำนวณ Margin Percentage
     * Margin = ((Price - Cost) / Price) × 100
     */
    public BigDecimal calculateMarginPercentage(Product product) {
        if (product == null ||
                product.getCalculatedCost() == null ||
                product.getSellingPrice() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal price = product.getSellingPrice();

        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal cost = product.getCalculatedCost();
        BigDecimal profit = price.subtract(cost);

        // Margin = (Profit / Price) × 100
        return profit
                .divide(price, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * ✅ เพิ่ม: แสดงรายละเอียดต้นทุนแต่ละ Ingredient (for debugging)
     */
    public String getCostBreakdown(Product product) {
        if (product == null) {
            return "Product is null";
        }

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            return "No ingredients found for product: " + product.getProductName();
        }

        StringBuilder breakdown = new StringBuilder();
        breakdown.append("Cost Breakdown for: ").append(product.getProductName()).append("\n");
        breakdown.append("==========================================\n");

        BigDecimal total = BigDecimal.ZERO;

        for (ProductIngredient ingredient : ingredients) {
            BigDecimal cost = calculateIngredientCost(ingredient);
            total = total.add(cost);

            breakdown.append(String.format("- %s: %.2f %s × %.2f = ฿%.2f\n",
                    ingredient.getIngredientName(),
                    ingredient.getRequiredQuantity(),
                    ingredient.getUnit(),
                    ingredient.getCostPerUnit(),
                    cost
            ));
        }

        breakdown.append("==========================================\n");
        breakdown.append(String.format("Total Cost: ฿%.2f\n", total));

        if (product.getSellingPrice() != null) {
            BigDecimal profit = product.getSellingPrice().subtract(total);
            breakdown.append(String.format("Selling Price: ฿%.2f\n", product.getSellingPrice()));
            breakdown.append(String.format("Profit: ฿%.2f (%.2f%%)\n",
                    profit,
                    calculateProfitPercentage(product)
            ));
        }

        return breakdown.toString();
    }
}