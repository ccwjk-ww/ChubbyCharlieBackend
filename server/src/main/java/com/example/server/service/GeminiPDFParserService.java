package com.example.server.service;

import com.example.server.entity.OrderItem;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeminiPDFParserService {

    @Autowired
    private GeminiAIService geminiAIService;

    private final Gson gson;

    public GeminiPDFParserService() {
        this.gson = new Gson();
    }

    /**
     * Parse รายการสินค้าจาก PDF โดยใช้ Gemini AI
     */
    public List<OrderItem> parseOrderItemsFromPDF(MultipartFile file) throws IOException {
        try {
            System.out.println("========== Starting Gemini PDF Parse ==========");
            System.out.println("File: " + file.getOriginalFilename());
            System.out.println("Size: " + file.getSize() + " bytes");

            // 1. เรียก Gemini AI เพื่อวิเคราะห์ PDF
            String geminiResponse = geminiAIService.analyzePDFWithGemini(file);

            System.out.println("📄 Gemini Response:");
            System.out.println(geminiResponse);

            // 2. Parse JSON response
            List<OrderItem> items = parseItemsFromJSON(geminiResponse);

            System.out.println("========== Parse Summary ==========");
            System.out.println("Total items parsed: " + items.size());
            System.out.println("===================================");

            return items;

        } catch (Exception e) {
            System.err.println("❌ Error in Gemini PDF parsing: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to parse PDF with Gemini: " + e.getMessage(), e);
        }
    }

    /**
     * แปลง JSON response จาก Gemini เป็น List<OrderItem>
     */
    private List<OrderItem> parseItemsFromJSON(String jsonResponse) {
        List<OrderItem> items = new ArrayList<>();

        try {
            JsonObject jsonObject = gson.fromJson(jsonResponse, JsonObject.class);
            JsonArray itemsArray = jsonObject.getAsJsonArray("items");

            if (itemsArray == null || itemsArray.size() == 0) {
                System.err.println("⚠️ No items found in Gemini response");
                return items;
            }

            int itemCount = 0;
            for (int i = 0; i < itemsArray.size(); i++) {
                try {
                    JsonObject itemJson = itemsArray.get(i).getAsJsonObject();
                    OrderItem item = createOrderItemFromJson(itemJson);

                    if (item != null) {
                        items.add(item);
                        itemCount++;

                        System.out.println("✓ Parsed item #" + itemCount + ": " +
                                "SKU=" + item.getProductSku() +
                                ", Name=" + item.getProductName() +
                                ", Qty=" + item.getQuantity() +
                                ", UnitPrice=" + item.getUnitPrice() +
                                ", Total=" + item.getTotalPrice());
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error parsing item " + i + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error parsing JSON response: " + e.getMessage());
            e.printStackTrace();
        }

        return items;
    }

    /**
     * สร้าง OrderItem จาก JSON object
     */
    private OrderItem createOrderItemFromJson(JsonObject itemJson) {
        try {
            OrderItem item = new OrderItem();

            // SKU
            if (itemJson.has("productSku")) {
                String sku = itemJson.get("productSku").getAsString().trim();
                item.setProductSku(sku);
            }

            // Product Name
            if (itemJson.has("productName")) {
                String name = itemJson.get("productName").getAsString().trim();
                item.setProductName(name);
            }

            // Quantity
            if (itemJson.has("quantity")) {
                int quantity = itemJson.get("quantity").getAsInt();
                item.setQuantity(quantity);
            } else {
                item.setQuantity(1); // default
            }

            // Unit Price
            if (itemJson.has("unitPrice")) {
                BigDecimal unitPrice = new BigDecimal(itemJson.get("unitPrice").getAsString());
                item.setUnitPrice(unitPrice);
            } else {
                item.setUnitPrice(BigDecimal.ZERO);
            }

            // Total Price
            if (itemJson.has("totalPrice")) {
                BigDecimal totalPrice = new BigDecimal(itemJson.get("totalPrice").getAsString());
                item.setTotalPrice(totalPrice);
            } else {
                item.setTotalPrice(BigDecimal.ZERO);
            }

            // Default values
            item.setDiscount(BigDecimal.ZERO);
            item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);

            // Validate
            if (item.getProductSku() == null || item.getProductSku().isEmpty()) {
                System.err.println("⚠️ Item missing SKU, skipping");
                return null;
            }

            return item;

        } catch (Exception e) {
            System.err.println("❌ Error creating OrderItem: " + e.getMessage());
            return null;
        }
    }

    /**
     * สำหรับ preview - แปลง items เป็น simple map
     */
    public List<java.util.Map<String, Object>> parseAndPreview(MultipartFile file) throws IOException {
        List<OrderItem> items = parseOrderItemsFromPDF(file);
        List<java.util.Map<String, Object>> preview = new ArrayList<>();

        for (OrderItem item : items) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("productSku", item.getProductSku());
            map.put("productName", item.getProductName());
            map.put("quantity", item.getQuantity());
            map.put("unitPrice", item.getUnitPrice());
            map.put("totalPrice", item.getTotalPrice());
            preview.add(map);
        }

        return preview;
    }
}