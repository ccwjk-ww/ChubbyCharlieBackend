package com.example.server.service;

import com.example.server.entity.Customer;
import com.example.server.entity.Order;
import com.example.server.entity.OrderItem;
import com.example.server.entity.Product;
import com.example.server.respository.CustomerRepository;
import com.example.server.respository.ProductRepository;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class GeminiTiktokExcelParserService {

    @Autowired
    private GeminiAIService geminiAIService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    /**
     * ⭐ Parse TikTok Excel - รองรับหลาย Orders
     */
    public List<Order> parseTiktokOrdersWithGemini(MultipartFile file, Long customerId, String customerName)
            throws IOException {

        // 1. ส่งไปให้ Gemini AI วิเคราะห์
        String geminiResponse = geminiAIService.analyzeTiktokExcelWithGemini(file);

        // 2. Parse JSON response from Gemini
        List<Order> orders = parseOrdersFromGeminiJSON(geminiResponse, customerId, customerName);

        return orders;
    }

    /**
     * ⭐ Parse หลาย Orders จาก Gemini JSON
     */
    private List<Order> parseOrdersFromGeminiJSON(String jsonResponse, Long customerId, String customerName) {
        List<Order> orders = new ArrayList<>();

        try {
            // Clean JSON
            String cleanJson = jsonResponse.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            JsonObject jsonObject = JsonParser.parseString(cleanJson).getAsJsonObject();

            // ตรวจสอบว่ามี orders array หรือไม่
            if (jsonObject.has("orders")) {
                JsonArray ordersArray = jsonObject.getAsJsonArray("orders");

                for (int i = 0; i < ordersArray.size(); i++) {
                    JsonObject orderJson = ordersArray.get(i).getAsJsonObject();
                    Order order = parseSingleOrder(orderJson, customerId, customerName);
                    if (order != null) {
                        orders.add(order);
                    }
                }
            } else {
                // Single order (backward compatibility)
                Order order = parseSingleOrder(jsonObject, customerId, customerName);
                if (order != null) {
                    orders.add(order);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("ไม่สามารถแปลงข้อมูลจาก Gemini AI: " + e.getMessage(), e);
        }

        return orders;
    }

    /**
     * ⭐ Parse Order เดียวจาก JSON
     */
    private Order parseSingleOrder(JsonObject jsonObject, Long customerId, String customerName) {
        try {
            Order order = new Order();

            // Order Number (PO Number)
            String orderNumber = getJsonString(jsonObject, "orderNumber", "TT-" + System.currentTimeMillis());
            order.setOrderNumber(orderNumber);

            // Customer
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("Customer not found: " + customerId));
            order.setCustomer(customer);
            order.setCustomerName(customerName);

            // Dates
            order.setOrderDate(parseDateTime(getJsonString(jsonObject, "orderCreatedTime", null)));
            order.setDeliveryDate(parseDateTime(getJsonString(jsonObject, "orderSettledTime", null)));

            // ⭐ Amounts
            BigDecimal totalRevenue = getJsonBigDecimal(jsonObject, "totalRevenue");
            BigDecimal totalFees = getJsonBigDecimal(jsonObject, "totalFees");
            BigDecimal netAmount = getJsonBigDecimal(jsonObject, "totalSettlementAmount");

            order.setTotalAmount(totalRevenue);
            order.setDiscount(totalFees.abs());
            order.setNetAmount(netAmount);
            order.setShippingFee(BigDecimal.ZERO);

            // ⭐ Source & Status - เหมือน 24Shop (Manual Mode)
            order.setSource(Order.OrderSource.TIKTOK);
            order.setStatus(Order.OrderStatus.PENDING);        // ⭐ รอดำเนินการ
            order.setPaymentStatus(Order.PaymentStatus.UNPAID); // ⭐ ยังไม่ชำระ

            // ⭐ Items
            List<OrderItem> items = new ArrayList<>();
            if (jsonObject.has("items")) {
                JsonArray itemsArray = jsonObject.getAsJsonArray("items");

                for (int i = 0; i < itemsArray.size(); i++) {
                    JsonObject itemObj = itemsArray.get(i).getAsJsonObject();
                    OrderItem item = createOrderItemFromJson(itemObj, order);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }

            order.setOrderItems(items);

            return order;

        } catch (Exception e) {
            System.err.println("Error parsing order: " + e.getMessage());
            return null;
        }
    }

    /**
     * ⭐ Create OrderItem พร้อมค้นหา Product
     */
    private OrderItem createOrderItemFromJson(JsonObject itemJson, Order order) {
        try {
            OrderItem item = new OrderItem();

            // Shopping center item format: "1729997094462589879 * 3"
            String shoppingItem = getJsonString(itemJson, "shoppingCenterItem", null);

            if (shoppingItem != null && !shoppingItem.trim().isEmpty()) {
                String[] parts = shoppingItem.split("\\*");

                if (parts.length >= 2) {
                    String sku = parts[0].trim();
                    int quantity = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));

                    item.setQuantity(quantity);

                    // ⭐ ค้นหา Product
                    Optional<Product> productOpt = productRepository.findBySku(sku);

                    if (productOpt.isPresent()) {
                        Product product = productOpt.get();
                        item.setProduct(product);
                        item.setProductName(product.getProductName());
                        item.setProductSku(product.getSku());

                        // ใช้ราคาจาก Product
                        if (product.getSellingPrice() != null) {
                            item.setUnitPrice(product.getSellingPrice());
                            item.setTotalPrice(product.getSellingPrice().multiply(BigDecimal.valueOf(quantity)));
                        }

                        if (product.getCalculatedCost() != null) {
                            item.setCostPerUnit(product.getCalculatedCost());
                        }
                    } else {
                        // ไม่พบ Product
                        item.setProductSku(sku);
                        item.setProductName("TikTok Product - " + sku);

                        // คำนวณราคาจาก Order Total
                        if (order != null && order.getTotalAmount() != null && quantity > 0) {
                            BigDecimal estimatedPrice = order.getTotalAmount().divide(
                                    BigDecimal.valueOf(quantity), 2, BigDecimal.ROUND_HALF_UP);
                            item.setUnitPrice(estimatedPrice);
                            item.setTotalPrice(estimatedPrice.multiply(BigDecimal.valueOf(quantity)));
                        } else {
                            item.setUnitPrice(BigDecimal.ZERO);
                            item.setTotalPrice(BigDecimal.ZERO);
                        }
                    }
                }
            }

            item.setDiscount(BigDecimal.ZERO);
            item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);

            return item;

        } catch (Exception e) {
            System.err.println("Error creating OrderItem: " + e.getMessage());
            return null;
        }
    }

    /**
     * ⭐ Preview TikTok Excel - หลาย Orders
     */
    public Map<String, Object> parseAndPreviewWithGemini(MultipartFile file) throws IOException {
        String geminiResponse = geminiAIService.analyzeTiktokExcelWithGemini(file);

        Map<String, Object> preview = new HashMap<>();

        try {
            String cleanJson = geminiResponse.trim();
            if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7);
            if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            cleanJson = cleanJson.trim();

            JsonObject jsonResponse = JsonParser.parseString(cleanJson).getAsJsonObject();

            List<Map<String, Object>> ordersList = new ArrayList<>();

            if (jsonResponse.has("orders")) {
                JsonArray ordersArray = jsonResponse.getAsJsonArray("orders");

                for (int i = 0; i < ordersArray.size(); i++) {
                    JsonObject orderObj = ordersArray.get(i).getAsJsonObject();
                    Map<String, Object> orderData = convertJsonToPreviewMap(orderObj);
                    ordersList.add(orderData);
                }
            } else {
                Map<String, Object> orderData = convertJsonToPreviewMap(jsonResponse);
                ordersList.add(orderData);
            }

            preview.put("orders", ordersList);
            preview.put("totalOrders", ordersList.size());
            preview.put("success", true);
            preview.put("parsedWith", "Gemini AI");

        } catch (Exception e) {
            preview.put("success", false);
            preview.put("message", "เกิดข้อผิดพลาด: " + e.getMessage());
        }

        return preview;
    }

    /**
     * ⭐ Convert JSON to Preview Map
     */
    private Map<String, Object> convertJsonToPreviewMap(JsonObject jsonObject) {
        Map<String, Object> orderData = new HashMap<>();

        // Basic Order Info
        orderData.put("orderNumber", getJsonString(jsonObject, "orderNumber", "N/A"));
        orderData.put("orderDate", getJsonString(jsonObject, "orderCreatedTime", ""));
        orderData.put("deliveryDate", getJsonString(jsonObject, "orderSettledTime", ""));

        BigDecimal totalRevenue = getJsonBigDecimal(jsonObject, "totalRevenue");
        BigDecimal totalFees = getJsonBigDecimal(jsonObject, "totalFees");
        BigDecimal netAmount = getJsonBigDecimal(jsonObject, "totalSettlementAmount");

        orderData.put("totalAmount", totalRevenue);
        orderData.put("discount", totalFees.abs()); // ⭐ แปลงเป็นบวก
        orderData.put("netAmount", netAmount);
        orderData.put("customerName", "TikTok Customer (ยังไม่ระบุ)");
        orderData.put("source", "TIKTOK");

        // Items
        List<Map<String, Object>> simpleItems = new ArrayList<>();

        if (jsonObject.has("items")) {
            JsonArray itemsArray = jsonObject.getAsJsonArray("items");

            for (int i = 0; i < itemsArray.size(); i++) {
                JsonObject itemObj = itemsArray.get(i).getAsJsonObject();
                String shoppingItem = getJsonString(itemObj, "shoppingCenterItem", "");

                if (!shoppingItem.isEmpty()) {
                    String[] parts = shoppingItem.split("\\*");
                    if (parts.length >= 2) {
                        String sku = parts[0].trim();
                        int qty = Integer.parseInt(parts[1].trim().replaceAll("[^0-9]", ""));

                        Map<String, Object> itemData = new HashMap<>();

                        // ค้นหา Product
                        Optional<Product> productOpt = productRepository.findBySku(sku);

                        if (productOpt.isPresent()) {
                            Product product = productOpt.get();
                            itemData.put("productSku", product.getSku());
                            itemData.put("productName", product.getProductName());
                            itemData.put("quantity", qty);
                            itemData.put("unitPrice", product.getSellingPrice());
                            itemData.put("totalPrice", product.getSellingPrice().multiply(BigDecimal.valueOf(qty)));
                            itemData.put("found", true);
                        } else {
                            itemData.put("productSku", sku);
                            itemData.put("productName", "⚠️ ไม่พบสินค้า - " + sku);
                            itemData.put("quantity", qty);
                            itemData.put("unitPrice", BigDecimal.ZERO);
                            itemData.put("totalPrice", BigDecimal.ZERO);
                            itemData.put("found", false);
                        }

                        simpleItems.add(itemData);
                    }
                }
            }
        }

        orderData.put("items", simpleItems);
        orderData.put("itemsCount", simpleItems.size());

        return orderData;
    }

    // Helper methods (เหมือนเดิม)
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDateTime.parse(dateStr.trim(), formatter);
                } catch (Exception ignored) {}
            }

            return LocalDateTime.now();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String getJsonString(JsonObject json, String key, String defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return defaultValue;
    }

    private BigDecimal getJsonBigDecimal(JsonObject json, String key) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            try {
                return new BigDecimal(json.get(key).getAsString()).setScale(2, BigDecimal.ROUND_HALF_UP);
            } catch (Exception e) {
                try {
                    return BigDecimal.valueOf(json.get(key).getAsDouble()).setScale(2, BigDecimal.ROUND_HALF_UP);
                } catch (Exception ex) {
                    return BigDecimal.ZERO;
                }
            }
        }
        return BigDecimal.ZERO;
    }
}