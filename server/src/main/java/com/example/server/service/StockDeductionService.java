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

    @Autowired
    private StockLotRepository stockLotRepository;

    /**
     * ⭐ เช็ค Stock พร้อมรายละเอียด Ingredient ทุกตัว + Stock Lot Information
     */
    public StockCheckDetailResponse checkStockWithDetails(OrderItem orderItem) {
        StockCheckDetailResponse response = new StockCheckDetailResponse();
        response.setOrderItemId(orderItem.getOrderItemId());
        response.setProductName(orderItem.getProductName());
        response.setOrderQuantity(orderItem.getQuantity());
        response.setIngredients(new ArrayList<>());

        Product product = findProduct(orderItem);
        if (product == null) {
            response.setAvailable(false);
            response.setErrorMessage("ไม่พบสินค้าในระบบ");
            return response;
        }

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            response.setAvailable(false);
            response.setErrorMessage("สินค้ายังไม่มี Ingredients");
            return response;
        }

        boolean allAvailable = true;

        for (ProductIngredient ingredient : ingredients) {
            IngredientStockDetail detail = new IngredientStockDetail();
            detail.setIngredientName(ingredient.getIngredientName());
            detail.setUnit(ingredient.getUnit());

            int quantityNeeded = calculateRequiredQuantity(orderItem, ingredient);
            detail.setRequiredQuantity(quantityNeeded);

            if (ingredient.getStockItem() == null) {
                detail.setAvailable(false);
                detail.setErrorMessage("ไม่มี Stock Item ที่เชื่อมโยง");
                allAvailable = false;
            } else {
                StockBase stockItem = ingredient.getStockItem();
                detail.setStockItemId(stockItem.getStockItemId());
                detail.setStockItemName(stockItem.getName());
                detail.setStockType(stockItem.getStockType());

                if (stockItem.getStockLotId() != null) {
                    stockLotRepository.findById(stockItem.getStockLotId())
                            .ifPresent(stockLot -> {
                                detail.setStockLotId(stockLot.getStockLotId());
                                detail.setStockLotName(stockLot.getLotName());
                                detail.setStockLotStatus(stockLot.getStatus().name());
                            });
                }

                Integer currentStock = stockItem.getQuantity();
                detail.setCurrentStock(currentStock != null ? currentStock : 0);

                if (currentStock != null && currentStock >= quantityNeeded) {
                    detail.setAvailable(true);
                } else {
                    detail.setAvailable(false);
                    int shortage = quantityNeeded - (currentStock != null ? currentStock : 0);
                    detail.setShortage(shortage);
                    detail.setErrorMessage("Stock ไม่เพียงพอ");
                    allAvailable = false;
                }
            }

            response.getIngredients().add(detail);
        }

        response.setAvailable(allAvailable);
        return response;
    }

    @Transactional
    public List<String> deductStockForOrderItem(OrderItem orderItem) {
        List<String> messages = new ArrayList<>();

        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        messages.add(String.format("🔄 เริ่มตัด Stock: %s (จำนวน: %d)",
                orderItem.getProductName(), orderItem.getQuantity()));
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (orderItem.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED) {
            messages.add("⏭️ ข้าม: รายการนี้ตัด Stock เรียบร้อยแล้ว");
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

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

        boolean allSuccess = true;
        List<String> failedIngredients = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < ingredients.size(); i++) {
            ProductIngredient ingredient = ingredients.get(i);

            messages.add(String.format("📦 [%d/%d] %s",
                    i + 1, ingredients.size(), ingredient.getIngredientName()));

            if (ingredient.getStockItem() == null) {
                String msg = "   ❌ ไม่มี Stock Item ที่เชื่อมโยง";
                messages.add(msg);
                failedIngredients.add(ingredient.getIngredientName());
                allSuccess = false;
                continue;
            }

            int quantityNeeded = calculateRequiredQuantity(orderItem, ingredient);
            messages.add(String.format("   📊 ต้องการ: %d %s", quantityNeeded, ingredient.getUnit()));

            StockDeductionResult result = safeDeduct(
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

            messages.add("");
        }

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

    @Transactional
    public List<String> deductStockForOrder(Order order) {
        List<String> allMessages = new ArrayList<>();

        allMessages.add("╔═══════════════════════════════════════╗");
        allMessages.add(String.format("║  ตัด Stock: Order %s", order.getOrderNumber()));
        allMessages.add("╚═══════════════════════════════════════╝");
        allMessages.add("");

        int successCount = 0;
        int failCount = 0;
        int skippedCount = 0;

        for (OrderItem item : order.getOrderItems()) {
            if (item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED) {
                allMessages.add(String.format(
                        "⏭️ ข้าม: %s (ตัดแล้ว)",
                        item.getProductName()
                ));
                skippedCount++;
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
        allMessages.add(String.format("║  สรุป: สำเร็จ %d | ข้าม %d | ล้มเหลว %d",
                successCount, skippedCount, failCount));
        allMessages.add("╚═══════════════════════════════════════╝");

        return allMessages;
    }

    // ============================================
    // ⭐ NEW: ระบบคืน Stock (Restore)
    // ============================================

    /**
     * ✅ คืน Stock สำหรับ Order Item เดียว
     */
    @Transactional
    public List<String> restoreStockForOrderItem(OrderItem orderItem) {
        List<String> messages = new ArrayList<>();

        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        messages.add(String.format("🔙 เริ่มคืน Stock: %s (จำนวน: %d)",
                orderItem.getProductName(), orderItem.getQuantity()));
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // ตรวจสอบสถานะ - ต้องเป็น COMPLETED ถึงจะคืนได้
        if (orderItem.getStockDeductionStatus() != OrderItem.StockDeductionStatus.COMPLETED) {
            messages.add("⏭️ ข้าม: รายการนี้ยังไม่ได้ตัด Stock (สถานะ: " +
                    orderItem.getStockDeductionStatus() + ")");
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        Product product = findProduct(orderItem);
        if (product == null) {
            String errorMsg = String.format(
                    "❌ ไม่พบสินค้า: %s (SKU: %s)",
                    orderItem.getProductName(),
                    orderItem.getProductSku()
            );
            messages.add(errorMsg);
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        messages.add(String.format("✓ พบสินค้า: %s (ID: %d)", product.getProductName(), product.getProductId()));

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            messages.add("⚠️ ไม่พบ Ingredients - ไม่สามารถคืน Stock ได้");
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        messages.add(String.format("✓ พบ %d ส่วนประกอบ", ingredients.size()));
        messages.add("");

        boolean allSuccess = true;
        List<String> failedIngredients = new ArrayList<>();
        int successCount = 0;

        for (int i = 0; i < ingredients.size(); i++) {
            ProductIngredient ingredient = ingredients.get(i);

            messages.add(String.format("📦 [%d/%d] %s",
                    i + 1, ingredients.size(), ingredient.getIngredientName()));

            if (ingredient.getStockItem() == null) {
                String msg = "   ❌ ไม่มี Stock Item ที่เชื่อมโยง";
                messages.add(msg);
                failedIngredients.add(ingredient.getIngredientName());
                allSuccess = false;
                continue;
            }

            int quantityToRestore = calculateRequiredQuantity(orderItem, ingredient);
            messages.add(String.format("   📊 จะคืน: %d %s", quantityToRestore, ingredient.getUnit()));

            StockRestoreResult result = safeRestore(
                    ingredient.getStockItem(),
                    quantityToRestore,
                    ingredient.getIngredientName(),
                    ingredient.getUnit()
            );

            if (result.success) {
                messages.add(String.format(
                        "   ✅ คืนสำเร็จ - คงเหลือ: %d %s",
                        result.newStock,
                        ingredient.getUnit()
                ));
                successCount++;
            } else {
                messages.add(String.format("   ❌ ล้มเหลว: %s", result.errorMessage));
                failedIngredients.add(ingredient.getIngredientName());
                allSuccess = false;
            }

            messages.add("");
        }

        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        if (allSuccess) {
            // เปลี่ยนสถานะกลับเป็น PENDING
            orderItem.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
            messages.add(String.format(
                    "✅ สำเร็จ! คืน Stock ทั้งหมด %d รายการ",
                    successCount
            ));
        } else {
            messages.add(String.format(
                    "⚠️ คืนบางส่วน! สำเร็จ %d/%d รายการ",
                    successCount, ingredients.size()
            ));
            messages.add("💔 รายการที่ล้มเหลว: " + String.join(", ", failedIngredients));
        }
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        orderItemRepository.save(orderItem);
        return messages;
    }

    /**
     * ✅ คืน Stock สำหรับ Order ทั้งหมด
     */
    @Transactional
    public List<String> restoreStockForOrder(Order order) {
        List<String> allMessages = new ArrayList<>();

        allMessages.add("╔═══════════════════════════════════════╗");
        allMessages.add(String.format("║  คืน Stock: Order %s", order.getOrderNumber()));
        allMessages.add("╚═══════════════════════════════════════╝");
        allMessages.add("");

        int successCount = 0;
        int failCount = 0;
        int skippedCount = 0;

        for (OrderItem item : order.getOrderItems()) {
            if (item.getStockDeductionStatus() != OrderItem.StockDeductionStatus.COMPLETED) {
                allMessages.add(String.format(
                        "⏭️ ข้าม: %s (ยังไม่ได้ตัด Stock)",
                        item.getProductName()
                ));
                skippedCount++;
                continue;
            }

            List<String> itemMessages = restoreStockForOrderItem(item);
            allMessages.addAll(itemMessages);
            allMessages.add("");

            if (item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.PENDING) {
                successCount++;
            } else {
                failCount++;
            }
        }

        allMessages.add("╔═══════════════════════════════════════╗");
        allMessages.add(String.format("║  สรุป: สำเร็จ %d | ข้าม %d | ล้มเหลว %d",
                successCount, skippedCount, failCount));
        allMessages.add("╚═══════════════════════════════════════╝");

        return allMessages;
    }

    /**
     * ✅ Safe Restore with retry (กรณีมีการแข่งขันเข้าถึง stock)
     */
    @Transactional
    public StockRestoreResult safeRestore(StockBase item, int qty, String name, String unit) {
        for (int i = 0; i < 2; i++) {
            try {
                return restoreStockToItem(item, qty, name, unit);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                // retry
            }
        }
        StockRestoreResult r = new StockRestoreResult();
        r.success = false;
        r.errorMessage = "คืน Stock ไม่สำเร็จ (ชนกันหลายครั้ง)";
        return r;
    }

    /**
     * ✅ คืน Stock เข้า Stock Item
     */
    protected StockRestoreResult restoreStockToItem(
            StockBase stockItem,
            int quantity,
            String ingredientName,
            String unit) {

        StockRestoreResult result = new StockRestoreResult();
        result.ingredientName = ingredientName;
        result.quantityRestored = quantity;

        if (stockItem == null) {
            result.success = false;
            result.errorMessage = "Stock Item เป็น null";
            return result;
        }

        final Long stockId = stockItem.getStockItemId();
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
            result.quantityRestored = 0;
            result.newStock = currentQty;
            return result;
        }

        // คืน Stock
        locked.setQuantity(currentQty + quantity);
        stockBaseRepository.saveAndFlush(locked);

        result.success = true;
        result.quantityRestored = quantity;
        result.newStock = locked.getQuantity();
        return result;
    }

    // ============================================
    // Existing Methods (ไม่เปลี่ยนแปลง)
    // ============================================

    @Transactional
    public StockDeductionResult safeDeduct(StockBase item, int qty, String name, String unit) {
        for (int i = 0; i < 2; i++) {
            try {
                return deductStockFromItem(item, qty, name, unit);
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
                // retry
            }
        }
        StockDeductionResult r = new StockDeductionResult();
        r.success = false;
        r.errorMessage = "ตัดสต็อกไม่สำเร็จ (ชนกันหลายครั้ง)";
        return r;
    }

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

        locked.setQuantity(currentQty - quantity);
        stockBaseRepository.saveAndFlush(locked);

        result.success = true;
        result.deductedQuantity = quantity;
        result.remainingStock = locked.getQuantity();
        return result;
    }

    private Product findProduct(OrderItem orderItem) {
        if (orderItem.getProduct() != null && orderItem.getProduct().getProductId() != null) {
            return productRepository.findById(orderItem.getProduct().getProductId()).orElse(null);
        }
        if (orderItem.getProductSku() != null && !orderItem.getProductSku().trim().isEmpty()) {
            return productRepository.findBySku(orderItem.getProductSku()).orElse(null);
        }
        if (orderItem.getProductName() != null && !orderItem.getProductName().trim().isEmpty()) {
            return productRepository.findByProductName(orderItem.getProductName()).orElse(null);
        }
        return null;
    }

    private int calculateRequiredQuantity(OrderItem orderItem, ProductIngredient ingredient) {
        int orderQuantity = orderItem.getQuantity() != null ? orderItem.getQuantity() : 1;
        if (ingredient.getRequiredQuantity() == null) {
            System.err.println("⚠️ Warning: requiredQuantity is null for ingredient: " +
                    ingredient.getIngredientName());
            return 0;
        }
        return (int) (orderQuantity * ingredient.getRequiredQuantity().doubleValue());
    }

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

    // ============================================
    // Response Classes
    // ============================================

    @lombok.Data
    public static class StockCheckDetailResponse {
        private Long orderItemId;
        private String productName;
        private Integer orderQuantity;
        private boolean available;
        private String errorMessage;
        private List<IngredientStockDetail> ingredients;
    }

    @lombok.Data
    public static class IngredientStockDetail {
        private String ingredientName;
        private String unit;
        private Integer requiredQuantity;
        private Long stockItemId;
        private String stockItemName;
        private Integer currentStock;
        private boolean available;
        private Integer shortage;
        private String errorMessage;
        private String stockType;
        private Long stockLotId;
        private String stockLotName;
        private String stockLotStatus;
    }

    private static class StockDeductionResult {
        boolean success;
        String ingredientName;
        int requestedQuantity;
        int deductedQuantity;
        int remainingStock;
        String errorMessage;
    }

    // ⭐ NEW: StockRestoreResult
    private static class StockRestoreResult {
        boolean success;
        String ingredientName;
        int quantityRestored;
        int newStock;
        String errorMessage;
    }
}