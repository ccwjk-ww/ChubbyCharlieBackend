package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class StockDeductionService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private StockBaseRepository stockBaseRepository;
    @Transactional
    public StockDeductionResult safeDeduct(StockBase item, int qty, String name, String unit) {
        for (int i = 0; i < 2; i++) {
            try {
                return deductStockFromItem(item, qty, name, unit);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                // retry รอบถัดไป
            }
        }
        StockDeductionResult r = new StockDeductionResult();
        r.success = false;
        r.errorMessage = "ตัดสต็อกไม่สำเร็จ (ชนกันหลายครั้ง)";
        return r;
    }

    /**
     * ✅ แก้ไข: ปรับปรุงการตัด Stock ให้ทำงานได้อย่างถูกต้อง
     */
    @Transactional
    public List<String> deductStockForOrderItem(OrderItem orderItem) {
        List<String> messages = new ArrayList<>();

        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        messages.add(String.format("🔄 เริ่มตัด Stock: %s (จำนวน: %d)",
                orderItem.getProductName(), orderItem.getQuantity()));
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 🔍 STEP 1: หา Product
        Product product = findProduct(orderItem);
        if (product == null) {
            String errorMsg = String.format(
                    "❌ ไม่พบสินค้า: %s (SKU: %s)",
                    orderItem.getProductName(),
                    orderItem.getProductSku()
            );
            messages.add(errorMsg);
            orderItem.setStockDeductionStatus(OrderItem.StockDeductionStatus.FAILED);
            orderItemRepository.save(orderItem);
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        messages.add(String.format("✓ พบสินค้า: %s (ID: %d)", product.getProductName(), product.getProductId()));

        // 🧩 STEP 2: ดึง Ingredients
        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            String errorMsg = String.format(
                    "⚠️ สินค้า '%s' ยังไม่มีส่วนประกอบ (Ingredients)\n" +
                            "💡 กรุณาเพิ่ม Ingredients ในหน้า Product Management",
                    product.getProductName()
            );
            messages.add(errorMsg);
            orderItem.setStockDeductionStatus(OrderItem.StockDeductionStatus.FAILED);
            orderItemRepository.save(orderItem);
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        messages.add(String.format("✓ พบ %d ส่วนประกอบ", ingredients.size()));
        messages.add("");

        // 🔄 STEP 3: Loop ตัด Stock แต่ละ Ingredient
        boolean allSuccess = true;
        List<String> failedIngredients = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < ingredients.size(); i++) {
            ProductIngredient ingredient = ingredients.get(i);

            messages.add(String.format("📦 [%d/%d] %s",
                    i + 1, ingredients.size(), ingredient.getIngredientName()));

            // ตรวจสอบ Stock Item
            if (ingredient.getStockItem() == null) {
                String msg = "   ❌ ไม่มี Stock Item ที่เชื่อมโยง";
                messages.add(msg);
                failedIngredients.add(ingredient.getIngredientName());
                allSuccess = false;
                continue;
            }

            // คำนวณจำนวนที่ต้องใช้
            int quantityNeeded = calculateRequiredQuantity(orderItem, ingredient);
            messages.add(String.format("   📊 ต้องการ: %d %s", quantityNeeded, ingredient.getUnit()));

            // ตัด Stock
            StockDeductionResult result = deductStockFromItem(
                    ingredient.getStockItem(),
                    quantityNeeded,
                    ingredient.getIngredientName(),
                    ingredient.getUnit()
            );

            if (result.success) {
                messages.add(String.format(
                        "   ✅ ตัดสำเร็จ - คงเหลือ: %d %s",
                        result.remainingStock,
                        ingredient.getUnit()
                ));
                successCount++;
            } else {
                messages.add(String.format("   ❌ ล้มเหลว: %s", result.errorMessage));
                failedIngredients.add(ingredient.getIngredientName());
                allSuccess = false;
            }

            messages.add(""); // blank line
        }

        // 📊 STEP 4: สรุปผลลัพธ์
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (allSuccess) {
            orderItem.setStockDeductionStatus(OrderItem.StockDeductionStatus.COMPLETED);
            messages.add(String.format(
                    "✅ สำเร็จ! ตัด Stock ทั้งหมด %d รายการ",
                    successCount
            ));
        } else {
            orderItem.setStockDeductionStatus(OrderItem.StockDeductionStatus.FAILED);
            messages.add(String.format(
                    "❌ ล้มเหลว! สำเร็จ %d/%d รายการ",
                    successCount, ingredients.size()
            ));
            messages.add("💔 รายการที่ล้มเหลว: " + String.join(", ", failedIngredients));
        }
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        orderItemRepository.save(orderItem);
        return messages;
    }

    /**
     * ✅ แก้ไข: หา Product จาก OrderItem
     */
    private Product findProduct(OrderItem orderItem) {
        // ลอง 1: จาก Product reference
        if (orderItem.getProduct() != null && orderItem.getProduct().getProductId() != null) {
            return productRepository.findById(orderItem.getProduct().getProductId()).orElse(null);
        }

        // ลอง 2: จาก SKU
        if (orderItem.getProductSku() != null && !orderItem.getProductSku().trim().isEmpty()) {
            return productRepository.findBySku(orderItem.getProductSku()).orElse(null);
        }

        // ลอง 3: จาก Product Name
        if (orderItem.getProductName() != null && !orderItem.getProductName().trim().isEmpty()) {
            return productRepository.findByProductName(orderItem.getProductName()).orElse(null);
        }

        return null;
    }

    /**
     * ✅ คำนวณจำนวนที่ต้องใช้
     */
    private int calculateRequiredQuantity(OrderItem orderItem, ProductIngredient ingredient) {
        int orderQuantity = orderItem.getQuantity() != null ? orderItem.getQuantity() : 1;

        // ตรวจสอบ requiredQuantity
        if (ingredient.getRequiredQuantity() == null) {
            System.err.println("⚠️ Warning: requiredQuantity is null for ingredient: " +
                    ingredient.getIngredientName());
            return 0;
        }

        return (int) (orderQuantity * ingredient.getRequiredQuantity().doubleValue());
    }

    // StockDeductionService.java (เฉพาะ method นี้)
    protected StockDeductionResult deductStockFromItem(
            StockBase stockItem,
            int quantity,
            String ingredientName,
            String unit) {

        StockDeductionResult result = new StockDeductionResult();
        result.ingredientName = ingredientName;
        result.requestedQuantity = quantity;

        if (stockItem == null) {
            result.success = false;
            result.errorMessage = "Stock Item เป็น null";
            return result;
        }

        final Long stockId = stockItem.getStockItemId();
        // ✅ ล็อกแถวก่อนอ่าน กันตัดพร้อมกัน
        StockBase locked = stockBaseRepository.lockById(stockId).orElse(null);
        if (locked == null) {
            result.success = false;
            result.errorMessage = "ไม่พบ Stock ID " + stockId;
            return result;
        }

        Integer currentQty = locked.getQuantity();
        if (currentQty == null) currentQty = 0;

        if (quantity <= 0) {
            result.success = true;
            result.deductedQuantity = 0;
            result.remainingStock = currentQty;
            return result;
        }

        if (currentQty < quantity) {
            result.success = false;
            result.errorMessage = String.format("Stock ไม่เพียงพอ (มี: %d %s, ต้องการ: %d %s)",
                    currentQty, unit, quantity, unit);
            result.remainingStock = currentQty;
            return result;
        }

        // ✅ หักสต็อกที่ entity ที่ล็อกไว้
        locked.setQuantity(currentQty - quantity);
        stockBaseRepository.saveAndFlush(locked);

        result.success = true;
        result.deductedQuantity = quantity;
        result.remainingStock = locked.getQuantity();
        return result;
    }


    /**
     * ✅ ตัด ChinaStock
     */
    @Transactional
    protected StockDeductionResult deductFromChinaStock(
            ChinaStock chinaStock,
            int quantity,
            String ingredientName,
            String unit) {

        StockDeductionResult result = new StockDeductionResult();
        result.ingredientName = ingredientName;
        result.requestedQuantity = quantity;

        Integer currentStock = chinaStock.getQuantity();

        if (currentStock == null) {
            result.success = false;
            result.errorMessage = "Stock quantity เป็น null";
            return result;
        }

        if (currentStock >= quantity) {
            chinaStock.setQuantity(currentStock - quantity);
            chinaStockRepository.save(chinaStock);
            chinaStockRepository.flush(); // Force save

            result.success = true;
            result.deductedQuantity = quantity;
            result.remainingStock = currentStock - quantity;
            return result;
        } else {
            result.success = false;
            result.errorMessage = String.format(
                    "Stock ไม่เพียงพอ (มี: %d %s, ต้องการ: %d %s)",
                    currentStock, unit, quantity, unit
            );
            result.remainingStock = currentStock;
            return result;
        }
    }

    /**
     * ✅ ตัด ThaiStock
     */
    @Transactional
    protected StockDeductionResult deductFromThaiStock(
            ThaiStock thaiStock,
            int quantity,
            String ingredientName,
            String unit) {

        StockDeductionResult result = new StockDeductionResult();
        result.ingredientName = ingredientName;
        result.requestedQuantity = quantity;

        Integer currentStock = thaiStock.getQuantity();

        if (currentStock == null) {
            result.success = false;
            result.errorMessage = "Stock quantity เป็น null";
            return result;
        }

        if (currentStock >= quantity) {
            thaiStock.setQuantity(currentStock - quantity);
            thaiStockRepository.save(thaiStock);
            thaiStockRepository.flush(); // Force save

            result.success = true;
            result.deductedQuantity = quantity;
            result.remainingStock = currentStock - quantity;
            return result;
        } else {
            result.success = false;
            result.errorMessage = String.format(
                    "Stock ไม่เพียงพอ (มี: %d %s, ต้องการ: %d %s)",
                    currentStock, unit, quantity, unit
            );
            result.remainingStock = currentStock;
            return result;
        }
    }

    /**
     * ✅ ตัด Stock สำหรับทั้ง Order
     */
    @Transactional
    public List<String> deductStockForOrder(Order order) {
        List<String> allMessages = new ArrayList<>();

        allMessages.add("╔═══════════════════════════════════════╗");
        allMessages.add(String.format("║  ตัด Stock: Order %s", order.getOrderNumber()));
        allMessages.add("╚═══════════════════════════════════════╝");
        allMessages.add("");

        int successCount = 0;
        int failCount = 0;

        for (OrderItem item : order.getOrderItems()) {
            if (item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED) {
                allMessages.add(String.format(
                        "⏭️ ข้าม: %s (ตัดแล้ว)",
                        item.getProductName()
                ));
                successCount++;
                continue;
            }

            List<String> itemMessages = deductStockForOrderItem(item);
            allMessages.addAll(itemMessages);
            allMessages.add("");

            if (item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED) {
                successCount++;
            } else {
                failCount++;
            }
        }

        allMessages.add("╔═══════════════════════════════════════╗");
        allMessages.add(String.format("║  สรุป: สำเร็จ %d | ล้มเหลว %d", successCount, failCount));
        allMessages.add("╚═══════════════════════════════════════╝");

        return allMessages;
    }

    /**
     * ✅ เช็ค Stock Availability
     */
    public boolean checkStockAvailability(OrderItem orderItem) {
        Product product = findProduct(orderItem);
        if (product == null) {
            return false;
        }

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            return false;
        }

        for (ProductIngredient ingredient : ingredients) {
            int quantityNeeded = calculateRequiredQuantity(orderItem, ingredient);

            if (!isStockSufficient(ingredient.getStockItem(), quantityNeeded)) {
                return false;
            }
        }

        return true;
    }

    /**
     * ✅ ตรวจสอบว่า Stock เพียงพอหรือไม่
     */
    private boolean isStockSufficient(StockBase stockItem, int quantity) {
        if (stockItem == null) {
            return false;
        }

        Integer currentQty = stockItem.getQuantity();
        if (currentQty == null) {
            return false;
        }

        return currentQty >= quantity;
    }

    /**
     * Helper class สำหรับเก็บผลลัพธ์
     */
    private static class StockDeductionResult {
        boolean success;
        String ingredientName;
        int requestedQuantity;
        int deductedQuantity;
        int remainingStock;
        String errorMessage;
    }
}