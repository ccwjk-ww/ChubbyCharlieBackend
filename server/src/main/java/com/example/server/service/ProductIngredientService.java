// ProductIngredientService.java - Service ใหม่
package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

// ProductIngredientService.java
@Service
@Transactional
public class ProductIngredientService {

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Autowired
    private ProductIngredientStockAllocationRepository allocationRepository;

    @Autowired
    private StockBaseRepository stockBaseRepository;

    /**
     * ⭐ สร้าง Ingredient แบบ Multi-Lot - แก้ไขให้บันทึกต้นทุนถูกต้อง
     */
    @Transactional
    public ProductIngredient createMultiLotIngredient(
            Product product,
            String ingredientName,
            BigDecimal requiredQuantity,
            String unit,
            List<StockAllocationRequest> allocations,
            String notes) {

        System.out.println("🔵 Creating Multi-Lot Ingredient:");
        System.out.println("  - Product: " + product.getProductName());
        System.out.println("  - Ingredient: " + ingredientName);
        System.out.println("  - Required Quantity: " + requiredQuantity);
        System.out.println("  - Allocations: " + allocations.size());

        // Validate
        validateAllocations(requiredQuantity, allocations);

        // 1️⃣ สร้าง ingredient
        ProductIngredient ingredient = new ProductIngredient();
        ingredient.setProduct(product);
        ingredient.setIngredientName(ingredientName);
        ingredient.setRequiredQuantity(requiredQuantity);
        ingredient.setUnit(unit);
        ingredient.setNotes(notes);
        ingredient.setAllocationMode(ProductIngredient.AllocationMode.MULTI_LOT);

        // ⭐ บันทึก ingredient ครั้งแรก
        ProductIngredient savedIngredient = productIngredientRepository.saveAndFlush(ingredient);
        System.out.println("✅ Step 1: Ingredient saved with ID: " + savedIngredient.getIngredientId());

        // 2️⃣ สร้าง allocations และคำนวณต้นทุน
        BigDecimal totalCost = BigDecimal.ZERO;
        int index = 1;

        for (StockAllocationRequest allocationReq : allocations) {
            System.out.println("  📌 Creating allocation #" + index);
            System.out.println("    - Stock ID: " + allocationReq.getStockItemId());
            System.out.println("    - Quantity: " + allocationReq.getAllocatedQuantity());
            System.out.println("    - Priority: " + allocationReq.getAllocationPriority());

            StockBase stock = stockBaseRepository.findById(allocationReq.getStockItemId())
                    .orElseThrow(() -> new RuntimeException("Stock not found: " + allocationReq.getStockItemId()));

            // ตรวจสอบสต็อกเพียงพอ
            if (stock.getQuantity() < allocationReq.getAllocatedQuantity().intValue()) {
                throw new RuntimeException("Insufficient stock for " + stock.getName());
            }

            BigDecimal unitCost = getStockUnitCost(stock);
            BigDecimal allocationTotal = unitCost.multiply(allocationReq.getAllocatedQuantity());

            System.out.println("    - Unit Cost: " + unitCost);
            System.out.println("    - Allocation Total: " + allocationTotal);

            ProductIngredientStockAllocation allocation = new ProductIngredientStockAllocation();
            allocation.setProductIngredient(savedIngredient);
            allocation.setStockItem(stock);
            allocation.setAllocatedQuantity(allocationReq.getAllocatedQuantity());
            allocation.setAllocationPriority(allocationReq.getAllocationPriority());
            allocation.setCostPerUnit(unitCost);
            allocation.setTotalCost(allocationTotal);

            allocationRepository.saveAndFlush(allocation);
            System.out.println("    ✅ Step 2." + index + ": Allocation saved");

            totalCost = totalCost.add(allocationTotal);
            index++;
        }

        System.out.println("💰 Step 3: Calculated Total Cost: " + totalCost);

        // 3️⃣ ⭐ สำคัญที่สุด: อัปเดตต้นทุนของ ingredient
        BigDecimal costPerUnit = requiredQuantity.compareTo(BigDecimal.ZERO) > 0 ?
                totalCost.divide(requiredQuantity, 4, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        savedIngredient.setTotalCost(totalCost);
        savedIngredient.setCostPerUnit(costPerUnit);

        System.out.println("  - Setting Total Cost: " + totalCost);
        System.out.println("  - Setting Cost Per Unit: " + costPerUnit);

        // ⭐ บันทึกครั้งที่ 2 เพื่ออัปเดตต้นทุน
        ProductIngredient finalIngredient = productIngredientRepository.saveAndFlush(savedIngredient);

        System.out.println("✅ Step 4: Ingredient costs updated");
        System.out.println("  - Final Total Cost: " + finalIngredient.getTotalCost());
        System.out.println("  - Final Cost Per Unit: " + finalIngredient.getCostPerUnit());

        // 4️⃣ ⭐ Verify ว่าบันทึกลง DB จริง
        ProductIngredient verified = productIngredientRepository.findById(finalIngredient.getIngredientId())
                .orElseThrow(() -> new RuntimeException("Ingredient not found after save"));

        System.out.println("✅ Step 5: Verification from DB:");
        System.out.println("  - DB Total Cost: " + verified.getTotalCost());
        System.out.println("  - DB Cost Per Unit: " + verified.getCostPerUnit());
        System.out.println("  - DB Allocations Count: " +
                (verified.getStockAllocations() != null ? verified.getStockAllocations().size() : 0));

        if (verified.getTotalCost() == null || verified.getTotalCost().compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("❌ ERROR: Ingredient costs not saved to database!");
        }

        return finalIngredient;
    }

    /**
     * ⭐ อัปเดต Allocations
     */
    @Transactional
    public ProductIngredient updateMultiLotAllocations(
            Long ingredientId,
            List<StockAllocationRequest> newAllocations) {

        ProductIngredient ingredient = productIngredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found"));

        validateAllocations(ingredient.getRequiredQuantity(), newAllocations);

        // ลบ allocations เก่า
        allocationRepository.deleteAll(ingredient.getStockAllocations());
        ingredient.clearStockAllocations();
        allocationRepository.flush();

        // สร้าง allocations ใหม่
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockAllocationRequest allocationReq : newAllocations) {
            StockBase stock = stockBaseRepository.findById(allocationReq.getStockItemId())
                    .orElseThrow(() -> new RuntimeException("Stock not found"));

            BigDecimal unitCost = getStockUnitCost(stock);
            BigDecimal allocationTotal = unitCost.multiply(allocationReq.getAllocatedQuantity());

            ProductIngredientStockAllocation allocation = new ProductIngredientStockAllocation();
            allocation.setProductIngredient(ingredient);
            allocation.setStockItem(stock);
            allocation.setAllocatedQuantity(allocationReq.getAllocatedQuantity());
            allocation.setAllocationPriority(allocationReq.getAllocationPriority());
            allocation.setCostPerUnit(unitCost);
            allocation.setTotalCost(allocationTotal);

            allocationRepository.saveAndFlush(allocation);

            totalCost = totalCost.add(allocationTotal);
        }

        // อัปเดตต้นทุน
        ingredient.setTotalCost(totalCost);
        ingredient.setCostPerUnit(
                totalCost.divide(ingredient.getRequiredQuantity(), 4, java.math.RoundingMode.HALF_UP)
        );

        return productIngredientRepository.saveAndFlush(ingredient);
    }

    /**
     * ⭐ ตรวจสอบ Allocations
     */
    private void validateAllocations(BigDecimal requiredQuantity, List<StockAllocationRequest> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            throw new IllegalArgumentException("Allocations cannot be empty");
        }

        // ตรวจสอบผลรวมต้องเท่ากับ requiredQuantity
        BigDecimal totalAllocated = allocations.stream()
                .map(StockAllocationRequest::getAllocatedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAllocated.compareTo(requiredQuantity) != 0) {
            throw new IllegalArgumentException(
                    String.format("Total allocated quantity (%.2f) must equal required quantity (%.2f)",
                            totalAllocated, requiredQuantity));
        }

        // ตรวจสอบ priority ไม่ซ้ำกัน
        long distinctPriorities = allocations.stream()
                .map(StockAllocationRequest::getAllocationPriority)
                .distinct()
                .count();

        if (distinctPriorities != allocations.size()) {
            throw new IllegalArgumentException("Allocation priorities must be unique");
        }
    }

    /**
     * ⭐ ดึงราคาต่อหน่วยจาก Stock
     */
    private BigDecimal getStockUnitCost(StockBase stock) {
        if (stock instanceof ChinaStock) {
            ChinaStock chinaStock = (ChinaStock) stock;
            BigDecimal cost = chinaStock.getFinalPricePerPair();
            if (cost == null || cost.compareTo(BigDecimal.ZERO) == 0) {
                cost = chinaStock.calculateFinalPrice();
            }
            return cost != null ? cost : BigDecimal.ZERO;
        } else if (stock instanceof ThaiStock) {
            ThaiStock thaiStock = (ThaiStock) stock;
            BigDecimal cost = thaiStock.getPricePerUnitWithShipping();
            if (cost == null || cost.compareTo(BigDecimal.ZERO) == 0) {
                cost = thaiStock.calculateFinalPrice();
            }
            return cost != null ? cost : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    @Data
    public static class StockAllocationRequest {
        private Long stockItemId;
        private BigDecimal allocatedQuantity;
        private Integer allocationPriority;
    }
}