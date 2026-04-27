package com.example.server.service;

import com.example.server.entity.*;
import com.example.server.respository.*;
import jakarta.persistence.OptimisticLockException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class StockDeductionService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Autowired
    private ProductIngredientStockAllocationRepository allocationRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private StockBaseRepository stockBaseRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    /**
     * ⭐ เช็ค Stock พร้อมรายละเอียด - รองรับทั้ง SINGLE และ MULTI_LOT (CASCADE)
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

            if (ingredient.getAllocationMode() == ProductIngredient.AllocationMode.MULTI_LOT) {
                List<ProductIngredientStockAllocation> allocations = ingredient.getStockAllocations()
                        .stream()
                        .sorted(Comparator.comparingInt(ProductIngredientStockAllocation::getAllocationPriority))
                        .collect(Collectors.toList());

                if (allocations.isEmpty()) {
                    detail.setAvailable(false);
                    detail.setErrorMessage("ไม่มี Stock Allocations ที่เชื่อมโยง");
                    allAvailable = false;
                } else {
                    List<StockAllocationDetail> allocationDetails = new ArrayList<>();
                    int totalAvailableStock = 0;
                    int remainingNeeded = quantityNeeded;

                    for (ProductIngredientStockAllocation allocation : allocations) {
                        StockBase stock = allocation.getStockItem();
                        if (stock == null) continue;

                        int currentStock = stock.getQuantity() != null ? stock.getQuantity() : 0;
                        totalAvailableStock += currentStock;

                        int willDeduct = Math.min(currentStock, remainingNeeded);
                        remainingNeeded -= willDeduct;

                        StockAllocationDetail allocDetail = new StockAllocationDetail();
                        allocDetail.setStockName(stock.getName());
                        allocDetail.setStockType(getStockType(stock));
                        allocDetail.setLotName(getLotName(stock));
                        allocDetail.setAllocatedQuantity(willDeduct);
                        allocDetail.setAvailableQuantity(currentStock);
                        allocDetail.setAllocationPriority(allocation.getAllocationPriority());
                        allocDetail.setAvailable(currentStock >= willDeduct);

                        allocationDetails.add(allocDetail);
                    }

                    detail.setStockAllocations(allocationDetails);

                    boolean hasEnoughStock = totalAvailableStock >= quantityNeeded;
                    detail.setAvailable(hasEnoughStock);

                    if (!hasEnoughStock) {
                        int shortage = quantityNeeded - totalAvailableStock;
                        detail.setErrorMessage(String.format("Stock ไม่เพียงพอ (ขาด %d %s)", shortage, ingredient.getUnit()));
                        allAvailable = false;
                    }
                }
            } else {
                if (ingredient.getStockItem() == null) {
                    detail.setAvailable(false);
                    detail.setErrorMessage("ไม่มี Stock Item ที่เชื่อมโยง");
                    allAvailable = false;
                } else {
                    StockBase stockItem = ingredient.getStockItem();
                    detail.setStockItemId(stockItem.getStockItemId());
                    detail.setStockItemName(stockItem.getName());
                    detail.setStockType(getStockType(stockItem));

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
            }

            response.getIngredients().add(detail);
        }

        response.setAvailable(allAvailable);
        return response;
    }

    /**
     * ⭐ ตัด Stock - รองรับทั้ง SINGLE และ MULTI_LOT (CASCADE)
     */
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

            messages.add(String.format("📦 [%d/%d] %s (%s)",
                    i + 1, ingredients.size(),
                    ingredient.getIngredientName(),
                    ingredient.getAllocationMode()));

            int quantityNeeded = calculateRequiredQuantity(orderItem, ingredient);
            messages.add(String.format("   📊 ต้องการทั้งหมด: %d %s", quantityNeeded, ingredient.getUnit()));

            if (ingredient.getAllocationMode() == ProductIngredient.AllocationMode.MULTI_LOT) {
                List<ProductIngredientStockAllocation> allocations = ingredient.getStockAllocations()
                        .stream()
                        .sorted(Comparator.comparingInt(ProductIngredientStockAllocation::getAllocationPriority))
                        .collect(Collectors.toList());

                if (allocations.isEmpty()) {
                    messages.add("   ❌ ไม่มี Stock Allocations");
                    failedIngredients.add(ingredient.getIngredientName());
                    allSuccess = false;
                    continue;
                }

                List<StockDeductionHistory> deductionHistory = new ArrayList<>();
                int remainingToDeduct = quantityNeeded;
                boolean ingredientSuccess = true;

                messages.add("   🔄 เริ่มตัดแบบ CASCADE (Priority ต่ำไปสูง):");
                messages.add("");

                for (ProductIngredientStockAllocation allocation : allocations) {
                    if (remainingToDeduct <= 0) {
                        messages.add(String.format(
                                "   ⏭️ Priority %d: %s - ข้าม (ตัดครบแล้ว)",
                                allocation.getAllocationPriority(),
                                allocation.getStockItem() != null ? allocation.getStockItem().getName() : "N/A"
                        ));
                        continue;
                    }

                    StockBase stock = allocation.getStockItem();
                    if (stock == null) {
                        messages.add(String.format(
                                "   ⚠️ Priority %d: Stock Item เป็น null",
                                allocation.getAllocationPriority()
                        ));
                        continue;
                    }

                    int currentStock = stock.getQuantity() != null ? stock.getQuantity() : 0;
                    int willDeduct = Math.min(currentStock, remainingToDeduct);

                    messages.add(String.format(
                            "   🔹 Priority %d: %s (มี: %d %s)",
                            allocation.getAllocationPriority(),
                            stock.getName(),
                            currentStock,
                            ingredient.getUnit()
                    ));
                    messages.add(String.format(
                            "      ▸ จะตัด: %d %s (เหลือต้องการ: %d %s)",
                            willDeduct,
                            ingredient.getUnit(),
                            remainingToDeduct,
                            ingredient.getUnit()
                    ));

                    if (willDeduct > 0) {
                        StockDeductionResult result = safeDeduct(
                                stock,
                                willDeduct,
                                ingredient.getIngredientName(),
                                ingredient.getUnit()
                        );

                        if (result.success) {
                            messages.add(String.format(
                                    "      ✅ ตัดสำเร็จ %d %s - คงเหลือ: %d %s",
                                    willDeduct,
                                    ingredient.getUnit(),
                                    result.remainingStock,
                                    ingredient.getUnit()
                            ));

                            StockDeductionHistory history = new StockDeductionHistory();
                            history.stockItemId = stock.getStockItemId();
                            history.quantityDeducted = willDeduct;
                            history.priority = allocation.getAllocationPriority();
                            deductionHistory.add(history);

                            remainingToDeduct -= willDeduct;
                        } else {
                            messages.add(String.format("      ❌ ล้มเหลว: %s", result.errorMessage));
                            ingredientSuccess = false;
                            break;
                        }
                    } else {
                        messages.add("      ⚠️ Stock หมด - ข้ามไป Priority ถัดไป");
                    }

                    messages.add("");
                }

                if (ingredientSuccess && remainingToDeduct == 0) {
                    messages.add(String.format("   ✅ ตัด %s สำเร็จทั้งหมด!", ingredient.getIngredientName()));
                    messages.add(String.format("      📊 สรุป: ตัดจาก %d Priority", deductionHistory.size()));
                    successCount++;

                    // ⭐ บันทึกประวัติ Transaction นี้
                    saveDeductionHistory(orderItem, ingredient, deductionHistory);
                } else if (remainingToDeduct > 0) {
                    messages.add(String.format(
                            "   ❌ Stock ไม่พอ! ยังขาดอีก %d %s",
                            remainingToDeduct,
                            ingredient.getUnit()
                    ));

                    messages.add("   🔄 Rollback - คืน Stock ที่ตัดไปแล้ว...");
                    rollbackDeduction(deductionHistory, ingredient.getUnit(), messages);

                    failedIngredients.add(ingredient.getIngredientName());
                    allSuccess = false;
                } else {
                    failedIngredients.add(ingredient.getIngredientName());
                    allSuccess = false;
                }

            } else {
                if (ingredient.getStockItem() == null) {
                    String msg = "   ❌ ไม่มี Stock Item ที่เชื่อมโยง";
                    messages.add(msg);
                    failedIngredients.add(ingredient.getIngredientName());
                    allSuccess = false;
                    continue;
                }

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

                    // ⭐ บันทึกประวัติ SINGLE mode
                    List<StockDeductionHistory> history = new ArrayList<>();
                    StockDeductionHistory h = new StockDeductionHistory();
                    h.stockItemId = ingredient.getStockItem().getStockItemId();
                    h.quantityDeducted = quantityNeeded;
                    h.priority = 1;
                    history.add(h);
                    saveDeductionHistory(orderItem, ingredient, history);
                } else {
                    messages.add(String.format("   ❌ ล้มเหลว: %s", result.errorMessage));
                    failedIngredients.add(ingredient.getIngredientName());
                    allSuccess = false;
                }
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

    /**
     * ⭐ Rollback - คืน Stock ที่ตัดไปแล้วกรณีล้มเหลว
     */
    private void rollbackDeduction(List<StockDeductionHistory> history, String unit, List<String> messages) {
        for (StockDeductionHistory record : history) {
            StockBase stock = stockBaseRepository.findById(record.stockItemId).orElse(null);
            if (stock != null) {
                StockRestoreResult result = safeRestore(stock, record.quantityDeducted, "", unit);
                if (result.success) {
                    messages.add(String.format(
                            "      ↩️ คืน %d %s กลับไปยัง %s",
                            record.quantityDeducted,
                            unit,
                            stock.getName()
                    ));
                }
            }
        }
    }

    /**
     * ⭐ บันทึกประวัติการตัด Stock (แต่ละ Transaction แยกกัน)
     * Format: DEDUCTION_HISTORY_<orderItemId>_<ingredientId>|<stockId>:<qty>:<priority>,<stockId>:<qty>:<priority>
     */
    private void saveDeductionHistory(OrderItem orderItem, ProductIngredient ingredient,
                                      List<StockDeductionHistory> history) {
        // สร้าง Unique Key สำหรับ Transaction นี้
        String historyKey = String.format("DEDUCTION_HISTORY_%d_%d",
                orderItem.getOrderItemId(),
                ingredient.getIngredientId());

        // สร้าง JSON ประวัติ
        StringBuilder historyJson = new StringBuilder();
        for (StockDeductionHistory h : history) {
            historyJson.append(h.stockItemId)
                    .append(":")
                    .append(h.quantityDeducted)
                    .append(":")
                    .append(h.priority)
                    .append(",");
        }

        // ลบ comma ตัวสุดท้าย
        if (historyJson.length() > 0) {
            historyJson.setLength(historyJson.length() - 1);
        }

        // ⭐ แทนที่ history เดิม (ไม่ append)
        String currentNotes = orderItem.getNotes() != null ? orderItem.getNotes() : "";
        String[] lines = currentNotes.split("\n");
        StringBuilder newNotes = new StringBuilder();

        // ลบ history เดิมของ ingredient นี้ออก
        for (String line : lines) {
            if (!line.isEmpty() && !line.startsWith(historyKey)) {
                if (newNotes.length() > 0) newNotes.append("\n");
                newNotes.append(line);
            }
        }

        // เพิ่ม history ใหม่
        if (newNotes.length() > 0) newNotes.append("\n");
        newNotes.append(historyKey).append("|").append(historyJson.toString());

        orderItem.setNotes(newNotes.toString());
    }

    /**
     * ⭐ คืน Stock - คืนเฉพาะ Transaction ล่าสุด
     */
    @Transactional
    public List<String> restoreStockForOrderItem(OrderItem orderItem) {
        List<String> messages = new ArrayList<>();

        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        messages.add(String.format("🔙 เริ่มคืน Stock: %s (จำนวน: %d)",
                orderItem.getProductName(), orderItem.getQuantity()));
        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        if (orderItem.getStockDeductionStatus() != OrderItem.StockDeductionStatus.COMPLETED) {
            messages.add("⏭️ ข้าม: รายการนี้ยังไม่ได้ตัด Stock");
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        Product product = findProduct(orderItem);
        if (product == null) {
            messages.add("❌ ไม่พบสินค้า");
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) {
            messages.add("⚠️ ไม่พบ Ingredients");
            messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return messages;
        }

        boolean allSuccess = true;
        int successCount = 0;

        for (ProductIngredient ingredient : ingredients) {
            messages.add(String.format("📦 %s (%s)",
                    ingredient.getIngredientName(),
                    ingredient.getAllocationMode()));

            // ⭐ โหลดประวัติของ Transaction ล่าสุด
            List<StockDeductionHistory> history = loadDeductionHistory(orderItem, ingredient);

            if (history.isEmpty()) {
                messages.add("   ⚠️ ไม่พบประวัติการตัด - ข้ามรายการนี้");
                messages.add("");
                continue;
            }

            // ⭐ คืน Stock ตามประวัติ (ย้อนลำดับ)
            messages.add(String.format("   📋 คืนประวัติการตัด (%d รายการ):", history.size()));
            boolean ingredientSuccess = true;

            for (int i = history.size() - 1; i >= 0; i--) {
                StockDeductionHistory record = history.get(i);
                StockBase stock = stockBaseRepository.findById(record.stockItemId).orElse(null);

                if (stock != null) {
                    StockRestoreResult result = safeRestore(stock, record.quantityDeducted,
                            ingredient.getIngredientName(), ingredient.getUnit());

                    if (result.success) {
                        messages.add(String.format(
                                "   ↩️ คืน %d %s → %s (Priority %d) - คงเหลือ: %d %s",
                                record.quantityDeducted,
                                ingredient.getUnit(),
                                stock.getName(),
                                record.priority,
                                result.newStock,
                                ingredient.getUnit()
                        ));
                    } else {
                        messages.add(String.format(
                                "   ❌ ล้มเหลว: %s",
                                result.errorMessage
                        ));
                        ingredientSuccess = false;
                    }
                }
            }

            if (ingredientSuccess) {
                // ⭐ ลบประวัติหลังคืนสำเร็จ
                clearDeductionHistory(orderItem, ingredient);
                messages.add("   ✅ คืน Stock สำเร็จ - ลบประวัติแล้ว");
                successCount++;
            }

            messages.add("");
        }

        if (allSuccess && successCount == ingredients.size()) {
            orderItem.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
            messages.add("✅ คืน Stock สำเร็จทั้งหมด");
        } else {
            messages.add("⚠️ คืน Stock บางส่วน");
        }

        messages.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        orderItemRepository.save(orderItem);
        return messages;
    }

    /**
     * ⭐ โหลดประวัติการตัด Stock ของ Transaction ล่าสุด
     */
    private List<StockDeductionHistory> loadDeductionHistory(OrderItem orderItem, ProductIngredient ingredient) {
        List<StockDeductionHistory> history = new ArrayList<>();

        String notes = orderItem.getNotes();
        if (notes == null || notes.isEmpty()) return history;

        String historyKey = String.format("DEDUCTION_HISTORY_%d_%d",
                orderItem.getOrderItemId(),
                ingredient.getIngredientId());

        String[] lines = notes.split("\n");
        for (String line : lines) {
            if (line.startsWith(historyKey)) {
                String[] parts = line.split("\\|");
                if (parts.length >= 2) {
                    String[] records = parts[1].split(",");
                    for (String record : records) {
                        if (!record.isEmpty()) {
                            String[] data = record.split(":");
                            if (data.length >= 3) {
                                StockDeductionHistory h = new StockDeductionHistory();
                                h.stockItemId = Long.parseLong(data[0]);
                                h.quantityDeducted = Integer.parseInt(data[1]);
                                h.priority = Integer.parseInt(data[2]);
                                history.add(h);
                            }
                        }
                    }
                }
                break;
            }
        }

        return history;
    }

    /**
     * ⭐ ลบประวัติการตัด Stock หลังคืนสำเร็จ
     */
    private void clearDeductionHistory(OrderItem orderItem, ProductIngredient ingredient) {
        String currentNotes = orderItem.getNotes();
        if (currentNotes == null || currentNotes.isEmpty()) return;

        String historyKey = String.format("DEDUCTION_HISTORY_%d_%d",
                orderItem.getOrderItemId(),
                ingredient.getIngredientId());

        String[] lines = currentNotes.split("\n");
        StringBuilder newNotes = new StringBuilder();

        for (String line : lines) {
            if (!line.isEmpty() && !line.startsWith(historyKey)) {
                if (newNotes.length() > 0) newNotes.append("\n");
                newNotes.append(line);
            }
        }

        orderItem.setNotes(newNotes.toString().trim());
    }

    // ============================================
    // Helper Methods
    // ============================================

    @Transactional
    public List<String> deductStockForOrder(Order order) {
        List<String> allMessages = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (OrderItem item : order.getOrderItems()) {
            List<String> itemMessages = deductStockForOrderItem(item);
            allMessages.addAll(itemMessages);

            if (item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED) {
                successCount++;
            } else {
                failCount++;
            }
        }

        allMessages.add(String.format("📊 สรุป: สำเร็จ %d | ล้มเหลว %d", successCount, failCount));
        return allMessages;
    }

    @Transactional
    public List<String> restoreStockForOrder(Order order) {
        List<String> allMessages = new ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            allMessages.addAll(restoreStockForOrderItem(item));
        }
        return allMessages;
    }

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
        r.errorMessage = "ตัดสต็อกไม่สำเร็จ";
        return r;
    }

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
        r.errorMessage = "คืน Stock ไม่สำเร็จ";
        return r;
    }

    protected StockDeductionResult deductStockFromItem(StockBase stockItem, int quantity, String ingredientName, String unit) {
        StockDeductionResult result = new StockDeductionResult();
        result.ingredientName = ingredientName;
        result.requestedQuantity = quantity;

        if (stockItem == null) {
            result.success = false;
            result.errorMessage = "Stock Item เป็น null";
            return result;
        }

        StockBase locked = stockBaseRepository.lockById(stockItem.getStockItemId()).orElse(null);
        if (locked == null) {
            result.success = false;
            result.errorMessage = "ไม่พบ Stock";
            return result;
        }

        Integer currentQty = locked.getQuantity() != null ? locked.getQuantity() : 0;

        if (currentQty < quantity) {
            result.success = false;
            result.errorMessage = String.format("Stock ไม่เพียงพอ (มี: %d, ต้องการ: %d)", currentQty, quantity);
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

    protected StockRestoreResult restoreStockToItem(StockBase stockItem, int quantity, String ingredientName, String unit) {
        StockRestoreResult result = new StockRestoreResult();

        if (stockItem == null) {
            result.success = false;
            return result;
        }

        StockBase locked = stockBaseRepository.lockById(stockItem.getStockItemId()).orElse(null);
        if (locked == null) {
            result.success = false;
            return result;
        }

        Integer currentQty = locked.getQuantity() != null ? locked.getQuantity() : 0;
        locked.setQuantity(currentQty + quantity);
        stockBaseRepository.saveAndFlush(locked);

        result.success = true;
        result.newStock = locked.getQuantity();
        return result;
    }

//    private Product findProduct(OrderItem orderItem) {
//        if (orderItem.getProduct() != null && orderItem.getProduct().getProductId() != null) {
//            return productRepository.findById(orderItem.getProduct().getProductId()).orElse(null);
//        }
//        if (orderItem.getProductSku() != null) {
//            return productRepository.findBySku(orderItem.getProductSku()).orElse(null);
//        }
//        if (orderItem.getProductName() != null) {
//            return productRepository.findByProductName(orderItem.getProductName()).orElse(null);
//        }
//        return null;
//    }

//    private Product findProduct(OrderItem orderItem) {
//        // 1. ลองหาด้วย product_id ก่อน
//        if (orderItem.getProduct() != null && orderItem.getProduct().getProductId() != null) {
//            Optional<Product> p = productRepository.findById(orderItem.getProduct().getProductId());
//            if (p.isPresent()) return p.get();
//        }
//
//        // 2. ลองหาด้วย SKU (exact)
//        if (orderItem.getProductSku() != null && !orderItem.getProductSku().trim().isEmpty()) {
//            Optional<Product> p = productRepository.findBySku(orderItem.getProductSku().trim());
//            if (p.isPresent()) return p.get();
//
//            // 3. ⭐ ลอง trim SKU แล้วค้นหาแบบ fuzzy (เผื่อ SKU มีช่องว่าง/ตัวอักษรพิเศษ)
//            System.out.println("⚠️ SKU not found exact: '" + orderItem.getProductSku() + "' for product: " + orderItem.getProductName());
//        }
//
//        // 4. ลองหาด้วย productName
//        if (orderItem.getProductName() != null && !orderItem.getProductName().trim().isEmpty()) {
//            Optional<Product> p = productRepository.findByProductName(orderItem.getProductName().trim());
//            if (p.isPresent()) {
//                System.out.println("✅ Found by name: " + orderItem.getProductName());
//                return p.get();
//            }
//            System.out.println("❌ Not found by name either: '" + orderItem.getProductName() + "'");
//        }
//
//        return null;
//    }

    private Product findProduct(OrderItem orderItem) {
        // 1. By product_id (เร็วที่สุด)
        if (orderItem.getProduct() != null && orderItem.getProduct().getProductId() != null) {
            Optional<Product> p = productRepository.findById(orderItem.getProduct().getProductId());
            if (p.isPresent()) return p.get();
        }

        // 2. By SKU เท่านั้น (simple และ reliable)
        if (orderItem.getProductSku() != null && !orderItem.getProductSku().trim().isEmpty()) {
            Optional<Product> p = productRepository.findBySku(orderItem.getProductSku().trim());
            if (p.isPresent()) return p.get();
            System.out.println("❌ SKU not found: " + orderItem.getProductSku());
        }

        return null;
    }

    private int calculateRequiredQuantity(OrderItem orderItem, ProductIngredient ingredient) {
        int orderQuantity = orderItem.getQuantity() != null ? orderItem.getQuantity() : 1;
        if (ingredient.getRequiredQuantity() == null) return 0;
        return (int) (orderQuantity * ingredient.getRequiredQuantity().doubleValue());
    }

    private String getStockType(StockBase stock) {
        if (stock instanceof ChinaStock) return "CHINA";
        if (stock instanceof ThaiStock) return "THAI";
        return null;
    }

    private String getLotName(StockBase stock) {
        if (stock instanceof ChinaStock) {
            return ((ChinaStock) stock).getName();
        }
        if (stock instanceof ThaiStock) {
            return ((ThaiStock) stock).getName();
        }
        return null;
    }

    public boolean checkStockAvailability(OrderItem orderItem) {
        Product product = findProduct(orderItem);
        if (product == null) return false;

        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(product.getProductId());

        if (ingredients == null || ingredients.isEmpty()) return false;

        for (ProductIngredient ingredient : ingredients) {
            int quantityNeeded = calculateRequiredQuantity(orderItem, ingredient);

            if (ingredient.getAllocationMode() == ProductIngredient.AllocationMode.MULTI_LOT) {
                int totalAvailable = 0;
                for (ProductIngredientStockAllocation allocation : ingredient.getStockAllocations()) {
                    StockBase stock = allocation.getStockItem();
                    if (stock != null && stock.getQuantity() != null) {
                        totalAvailable += stock.getQuantity();
                    }
                }
                if (totalAvailable < quantityNeeded) return false;
            } else {
                if (!isStockSufficient(ingredient.getStockItem(), quantityNeeded)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isStockSufficient(StockBase stockItem, int quantity) {
        if (stockItem == null) return false;
        Integer currentQty = stockItem.getQuantity();
        return currentQty != null && currentQty >= quantity;
    }

    // ============================================
    // Response Classes & Helper Classes
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
        private List<StockAllocationDetail> stockAllocations;
    }

    @lombok.Data
    public static class StockAllocationDetail {
        private String stockName;
        private String stockType;
        private String lotName;
        private int allocatedQuantity;
        private Integer availableQuantity;
        private int allocationPriority;
        private boolean available;
    }

    private static class StockDeductionResult {
        boolean success;
        String ingredientName;
        int requestedQuantity;
        int deductedQuantity;
        int remainingStock;
        String errorMessage;
    }

    private static class StockRestoreResult {
        boolean success;
        String ingredientName;
        int quantityRestored;
        int newStock;
        String errorMessage;
    }

    private static class StockDeductionHistory {
        Long stockItemId;
        int quantityDeducted;
        int priority;
    }
}
