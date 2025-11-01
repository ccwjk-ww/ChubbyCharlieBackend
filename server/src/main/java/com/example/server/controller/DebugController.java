package com.example.server.controller;

import com.example.server.entity.*;
import com.example.server.respository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductIngredientRepository productIngredientRepository;

    @Autowired
    private StockBaseRepository stockBaseRepository;

    @Autowired
    private ChinaStockRepository chinaStockRepository;

    @Autowired
    private ThaiStockRepository thaiStockRepository;

    /**
     * ✅ ตรวจสอบข้อมูล Product และ Ingredients
     */
    @GetMapping("/product/{productId}")
    public ResponseEntity<Map<String, Object>> debugProduct(@PathVariable Long productId) {
        Map<String, Object> debug = new LinkedHashMap<>();

        // 1. Product Info
        Optional<Product> productOpt = productRepository.findById(productId);
        if (!productOpt.isPresent()) {
            debug.put("error", "Product not found");
            return ResponseEntity.notFound().build();
        }

        Product product = productOpt.get();
        debug.put("productId", product.getProductId());
        debug.put("productName", product.getProductName());
        debug.put("sku", product.getSku());
        debug.put("status", product.getStatus());

        // 2. Ingredients Info
        List<ProductIngredient> ingredients = productIngredientRepository
                .findByProductProductId(productId);

        debug.put("ingredientsCount", ingredients.size());

        List<Map<String, Object>> ingredientsList = new ArrayList<>();
        for (ProductIngredient ing : ingredients) {
            Map<String, Object> ingInfo = new LinkedHashMap<>();
            ingInfo.put("ingredientId", ing.getIngredientId());
            ingInfo.put("ingredientName", ing.getIngredientName());
            ingInfo.put("requiredQuantity", ing.getRequiredQuantity());
            ingInfo.put("unit", ing.getUnit());
            ingInfo.put("stockType", ing.getStockType());

            // Stock Item Info
            if (ing.getStockItem() != null) {
                StockBase stockItem = ing.getStockItem();
                Map<String, Object> stockInfo = new LinkedHashMap<>();
                stockInfo.put("stockItemId", stockItem.getStockItemId());
                stockInfo.put("name", stockItem.getName());
                stockInfo.put("type", stockItem.getClass().getSimpleName());
                stockInfo.put("quantity", stockItem.getQuantity());
                stockInfo.put("status", stockItem.getStatus());
                stockInfo.put("quantityIsNull", stockItem.getQuantity() == null);

                ingInfo.put("stockItem", stockInfo);
            } else {
                ingInfo.put("stockItem", "NULL");
            }

            ingredientsList.add(ingInfo);
        }

        debug.put("ingredients", ingredientsList);

        return ResponseEntity.ok(debug);
    }

    /**
     * ✅ ตรวจสอบข้อมูล Stock Item
     */
    @GetMapping("/stock/{stockId}")
    public ResponseEntity<Map<String, Object>> debugStock(@PathVariable Long stockId) {
        Map<String, Object> debug = new LinkedHashMap<>();

        // Try StockBase
        Optional<StockBase> stockBaseOpt = stockBaseRepository.findById(stockId);
        if (stockBaseOpt.isPresent()) {
            StockBase stock = stockBaseOpt.get();
            debug.put("foundVia", "StockBaseRepository");
            debug.put("type", stock.getClass().getSimpleName());
            debug.put("stockItemId", stock.getStockItemId());
            debug.put("name", stock.getName());
            debug.put("quantity", stock.getQuantity());
            debug.put("quantityIsNull", stock.getQuantity() == null);
            debug.put("status", stock.getStatus());
        }

        // Try ChinaStock
        Optional<ChinaStock> chinaOpt = chinaStockRepository.findById(stockId);
        if (chinaOpt.isPresent()) {
            ChinaStock china = chinaOpt.get();
            debug.put("foundAsChinaStock", true);
            debug.put("chinaQuantity", china.getQuantity());
            debug.put("chinaQuantityIsNull", china.getQuantity() == null);
        }

        // Try ThaiStock
        Optional<ThaiStock> thaiOpt = thaiStockRepository.findById(stockId);
        if (thaiOpt.isPresent()) {
            ThaiStock thai = thaiOpt.get();
            debug.put("foundAsThaiStock", true);
            debug.put("thaiQuantity", thai.getQuantity());
            debug.put("thaiQuantityIsNull", thai.getQuantity() == null);
        }

        if (debug.isEmpty()) {
            debug.put("error", "Stock not found");
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(debug);
    }

    /**
     * ✅ อัพเดท Stock Quantity แบบ Manual (สำหรับ Debug)
     */
    @PatchMapping("/stock/{stockId}/quantity")
    public ResponseEntity<Map<String, Object>> updateStockQuantity(
            @PathVariable Long stockId,
            @RequestBody Map<String, Integer> request) {

        Integer newQuantity = request.get("quantity");
        if (newQuantity == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "quantity is required"));
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // Try ChinaStock first
        Optional<ChinaStock> chinaOpt = chinaStockRepository.findById(stockId);
        if (chinaOpt.isPresent()) {
            ChinaStock china = chinaOpt.get();
            china.setQuantity(newQuantity);
            chinaStockRepository.save(china);

            result.put("success", true);
            result.put("type", "ChinaStock");
            result.put("stockItemId", stockId);
            result.put("oldQuantity", china.getQuantity());
            result.put("newQuantity", newQuantity);
            return ResponseEntity.ok(result);
        }

        // Try ThaiStock
        Optional<ThaiStock> thaiOpt = thaiStockRepository.findById(stockId);
        if (thaiOpt.isPresent()) {
            ThaiStock thai = thaiOpt.get();
            thai.setQuantity(newQuantity);
            thaiStockRepository.save(thai);

            result.put("success", true);
            result.put("type", "ThaiStock");
            result.put("stockItemId", stockId);
            result.put("oldQuantity", thai.getQuantity());
            result.put("newQuantity", newQuantity);
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Stock not found"));
    }

    /**
     * ✅ ตรวจสอบ Order Item
     */
    @GetMapping("/order-item/{orderItemId}")
    public ResponseEntity<Map<String, Object>> debugOrderItem(@PathVariable Long orderItemId) {
        // Implementation here if needed
        return ResponseEntity.ok(Map.of("message", "Not implemented yet"));
    }
}