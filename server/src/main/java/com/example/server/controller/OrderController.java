package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.Customer;
import com.example.server.entity.Order;
import com.example.server.entity.OrderItem;
import com.example.server.entity.Product;
import com.example.server.mapper.OrderMapper;
import com.example.server.respository.CustomerRepository;
import com.example.server.respository.OrderItemRepository;
import com.example.server.respository.OrderRepository;
import com.example.server.respository.ProductRepository;
import com.example.server.service.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private GeminiPDFParserService geminiPDFParserService;

    @Autowired
    private ExcelParserService excelParserService;

    @Autowired
    private GeminiTiktokExcelParserService geminiTiktokExcelParserService;

    @Autowired
    private StockDeductionService stockDeductionService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ============================================
    // GET Endpoints (เหมือนเดิม)
    // ============================================

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long id) {
        Optional<Order> order = orderService.getOrderById(id);
        return order.map(o -> ResponseEntity.ok(orderMapper.toOrderDTO(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderDTO> getOrderByNumber(@PathVariable String orderNumber) {
        Optional<Order> order = orderService.getOrderByOrderNumber(orderNumber);
        return order.map(o -> ResponseEntity.ok(orderMapper.toOrderDTO(o)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderDTO>> getOrdersByStatus(@PathVariable String status) {
        Order.OrderStatus orderStatus = Order.OrderStatus.valueOf(status);
        List<Order> orders = orderService.getOrdersByStatus(orderStatus);
        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
    }

    @GetMapping("/source/{source}")
    public ResponseEntity<List<OrderDTO>> getOrdersBySource(@PathVariable String source) {
        Order.OrderSource orderSource = Order.OrderSource.valueOf(source);
        List<Order> orders = orderService.getOrdersBySource(orderSource);
        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
    }

    @GetMapping("/search")
    public ResponseEntity<List<OrderDTO>> searchOrders(@RequestParam String keyword) {
        List<Order> orders = orderService.searchOrders(keyword);
        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
    }

    @GetMapping("/{id}/check-stock-details")
    public ResponseEntity<?> checkStockDetails(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            List<StockDeductionService.StockCheckDetailResponse> details = new ArrayList<>();
            boolean allAvailable = true;

            for (OrderItem item : order.getOrderItems()) {
                StockDeductionService.StockCheckDetailResponse detail =
                        stockDeductionService.checkStockWithDetails(item);
                details.add(detail);

                if (!detail.isAvailable()) {
                    allAvailable = false;
                }
            }

            String message = allAvailable
                    ? "✅ Stock เพียงพอทั้งหมด พร้อมตัด Stock"
                    : "⚠️ Stock ไม่เพียงพอบางรายการ";

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "allAvailable", allAvailable,
                    "orderNumber", order.getOrderNumber(),
                    "totalItems", details.size(),
                    "details", details,
                    "message", message
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to check stock details: " + e.getMessage())
            );
        }
    }

    // ============================================
    // POST/PUT/PATCH Endpoints (เหมือนเดิม)
    // ============================================

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            Order order = convertToOrder(request);
            List<OrderItem> items = convertToOrderItems(request.getOrderItems());
            Order savedOrder = orderService.createOrder(order, items);

            // ⭐ ไม่ตัด Stock อัตโนมัติ - ให้ผู้ใช้ตัดเอง
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "✅ สร้าง Order สำเร็จ - กรุณาตัด Stock ด้วยตัวเอง",
                    "order", orderMapper.toOrderDTO(savedOrder)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to create order: " + e.getMessage())
            );
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateOrder(@PathVariable Long id, @RequestBody OrderCreateRequest request) {
        try {
            Order orderDetails = convertToOrder(request);
            List<OrderItem> newItems = null;
            if (request.getOrderItems() != null && !request.getOrderItems().isEmpty()) {
                newItems = convertToOrderItems(request.getOrderItems());
            }
            Order updatedOrder = orderService.updateOrder(id, orderDetails, newItems);
            return ResponseEntity.ok(orderMapper.toOrderDTO(updatedOrder));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to update order: " + e.getMessage())
            );
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        try {
            Order.OrderStatus status = Order.OrderStatus.valueOf(request.getStatus());
            Order updatedOrder = orderService.updateOrderStatus(id, status);
            return ResponseEntity.ok(orderMapper.toOrderDTO(updatedOrder));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to update status: " + e.getMessage())
            );
        }
    }

    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<?> updatePaymentStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        try {
            String paymentStatus = request.get("paymentStatus");
            if (paymentStatus == null || paymentStatus.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Payment status is required"
                ));
            }
            Order updatedOrder = orderService.updatePaymentStatus(id, Order.PaymentStatus.valueOf(paymentStatus));
            OrderDTO orderDTO = orderMapper.toOrderDTO(updatedOrder);
            return ResponseEntity.ok(orderDTO);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to update payment status: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
        try {
            Order cancelledOrder = orderService.cancelOrder(id);
            return ResponseEntity.ok(orderMapper.toOrderDTO(cancelledOrder));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to cancel order: " + e.getMessage())
            );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOrder(@PathVariable Long id) {
        try {
            orderService.deleteOrder(id);
            return ResponseEntity.ok(new ErrorResponse(true, "Order deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to delete order: " + e.getMessage())
            );
        }
    }

    // ============================================
    // Stock Management
    // ============================================

    @PostMapping("/{id}/deduct-stock")
    public ResponseEntity<?> deductStockForOrder(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            List<String> messages = stockDeductionService.deductStockForOrder(order);
            return ResponseEntity.ok(new StockDeductionResponse(true, "Stock deduction completed", messages));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to deduct stock: " + e.getMessage())
            );
        }
    }

    @GetMapping("/{id}/check-stock")
    public ResponseEntity<?> checkStockAvailability(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            boolean allAvailable = true;
            List<String> messages = new ArrayList<>();
            for (OrderItem item : order.getOrderItems()) {
                boolean available = stockDeductionService.checkStockAvailability(item);
                if (!available) {
                    allAvailable = false;
                    messages.add("✗ " + item.getProductName() + " - Stock ไม่เพียงพอ");
                } else {
                    messages.add("✓ " + item.getProductName() + " - Stock เพียงพอ");
                }
            }
            String message = allAvailable ? "Stock เพียงพอทั้งหมด" : "Stock ไม่เพียงพอบางรายการ";
            return ResponseEntity.ok(new StockCheckResponse(allAvailable, message, messages));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to check stock: " + e.getMessage())
            );
        }
    }

    @PostMapping("/{id}/restore-stock")
    public ResponseEntity<?> restoreStockForOrder(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            boolean hasCompletedItems = order.getOrderItems().stream()
                    .anyMatch(item -> item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED);

            if (!hasCompletedItems) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ไม่มีรายการที่ตัด Stock แล้ว ไม่สามารถคืนได้"
                ));
            }

            List<String> messages = stockDeductionService.restoreStockForOrder(order);
            return ResponseEntity.ok(new StockDeductionResponse(true, "Stock restoration completed", messages));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to restore stock: " + e.getMessage())
            );
        }
    }

    @GetMapping("/{id}/stock-deduction-status")
    public ResponseEntity<?> getStockDeductionStatus(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            int totalItems = order.getOrderItems().size();
            long completedCount = order.getOrderItems().stream()
                    .filter(item -> item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.COMPLETED)
                    .count();
            long pendingCount = order.getOrderItems().stream()
                    .filter(item -> item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.PENDING)
                    .count();
            long failedCount = order.getOrderItems().stream()
                    .filter(item -> item.getStockDeductionStatus() == OrderItem.StockDeductionStatus.FAILED)
                    .count();

            boolean allCompleted = completedCount == totalItems && totalItems > 0;
            boolean allPending = pendingCount == totalItems;
            boolean hasCompleted = completedCount > 0;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "totalItems", totalItems,
                    "completedCount", completedCount,
                    "pendingCount", pendingCount,
                    "failedCount", failedCount,
                    "allCompleted", allCompleted,
                    "allPending", allPending,
                    "hasCompleted", hasCompleted,
                    "canDeduct", !allCompleted && totalItems > 0,
                    "canRestore", hasCompleted
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    new ErrorResponse(false, "Failed to get stock deduction status: " + e.getMessage())
            );
        }
    }

    // ============================================
    // ⭐ TikTok Upload - รองรับหลาย Orders
    // ============================================

    @PostMapping("/upload/tiktok-excel")
    public ResponseEntity<?> uploadTiktokExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {

        try {
            System.out.println("========== TikTok Excel Upload Started ==========");
            System.out.println("File: " + file.getOriginalFilename());
            System.out.println("Customer ID: " + customerId);
            System.out.println("Auto Deduct Stock: " + autoDeductStock);

            // ⭐ Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ไฟล์ว่างเปล่า"
                ));
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "กรุณาอัพโหลดไฟล์ Excel (.xlsx)"
                ));
            }

            // ⭐ Validate customer
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("ไม่พบลูกค้า ID: " + customerId));

            System.out.println("Customer found: " + customer.getCustomerName());

            // ⭐ Parse orders using Gemini AI
            List<Order> orders = geminiTiktokExcelParserService.parseTiktokOrdersWithGemini(
                    file,
                    customerId,
                    customer.getCustomerName()
            );

            if (orders == null || orders.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Gemini AI ไม่พบ Orders ใน Excel"
                ));
            }

            System.out.println("Orders parsed: " + orders.size());

            // ⭐ Save orders
            List<Map<String, Object>> savedOrdersSummary = new ArrayList<>();
            int totalItems = 0;

            for (Order order : orders) {
                try {
                    order.setOriginalFileName(file.getOriginalFilename());

                    // Extract items before clearing
                    List<OrderItem> items = new ArrayList<>(order.getOrderItems());
                    order.getOrderItems().clear();

                    // Create order
                    Order savedOrder = orderService.createOrder(order, items);
                    totalItems += items.size();

                    savedOrdersSummary.add(Map.of(
                            "orderId", savedOrder.getOrderId(),
                            "orderNumber", savedOrder.getOrderNumber(),
                            "itemsCount", savedOrder.getOrderItems().size(),
                            "totalAmount", savedOrder.getTotalAmount(),
                            "netAmount", savedOrder.getNetAmount()
                    ));

                    System.out.println("✓ Order saved: " + savedOrder.getOrderNumber());

                } catch (Exception e) {
                    System.err.println("❌ Failed to save order: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // ⭐ Return success response
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "✅ อัพโหลด TikTok สำเร็จ - กรุณาตัด Stock ด้วยตัวเอง",
                    "totalOrders", orders.size(),
                    "totalItems", totalItems,
                    "orders", savedOrdersSummary,
                    "parsedWith", "Gemini AI",
                    "note", "⚠️ ต้องตัด Stock ด้วยตัวเองในหน้ารายละเอียด Order"
            ));

        } catch (Exception e) {
            System.err.println("========== TikTok Upload Failed ==========");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage(),
                    "error", e.getClass().getSimpleName()
            ));
        }
    }
    /**
     * ⭐ Preview TikTok Excel - รองรับหลาย PO
     */
    @PostMapping("/upload/preview-tiktok-excel")
    public ResponseEntity<?> previewTiktokExcel(@RequestParam("file") MultipartFile file) {

        try {
            System.out.println("========== TikTok Excel Preview Started ==========");

            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ไฟล์ว่างเปล่า"
                ));
            }

            String fileName = file.getOriginalFilename();
            if (fileName == null || !fileName.toLowerCase().endsWith(".xlsx")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "กรุณาอัพโหลดไฟล์ Excel (.xlsx)"
                ));
            }

            // ⭐ Parse and preview using Gemini AI
            Map<String, Object> preview = geminiTiktokExcelParserService.parseAndPreviewWithGemini(file);

            System.out.println("========== TikTok Excel Preview Completed ==========");
            System.out.println("Preview Success: " + preview.get("success"));
            System.out.println("Total Orders: " + preview.get("totalOrders"));

            return ResponseEntity.ok(preview);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("========== TikTok Excel Preview Failed ==========");
            System.err.println("Error: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "เกิดข้อผิดพลาด: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // 24Shop Upload (เหมือนเดิม)
    // ============================================

    @PostMapping("/upload/24shop-pdf")
    public ResponseEntity<?> upload24ShopPDF(
            @RequestParam("file") MultipartFile file,
            @RequestParam("orderNumber") String orderNumber,
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {
        try {
            System.out.println("========== 24Shop PDF Upload Started ==========");

            // ⚠️ แจ้งเตือนถ้าผู้ใช้เปิด Auto Deduct
            if (autoDeductStock) {
                System.out.println("⚠️ WARNING: autoDeductStock is enabled but will be IGNORED");
                System.out.println("   All orders require MANUAL stock deduction");
            }

            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "ไฟล์ต้องเป็น PDF เท่านั้น")
                );
            }

            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("ไม่พบข้อมูลลูกค้า ID: " + customerId));

            List<OrderItem> items = geminiPDFParserService.parseOrderItemsFromPDF(file);

            if (items.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "ไม่พบรายการสินค้าใน PDF")
                );
            }

            Order order = new Order();
            order.setOrderNumber(orderNumber);
            order.setSource(Order.OrderSource.SHOP_24);
            order.setCustomer(customer);
            order.setCustomerName(customer.getCustomerName());
            order.setCustomerPhone(customer.getCustomerPhone());
            order.setShippingAddress(customer.getCustomerAddress());
            order.setOrderDate(LocalDateTime.now());
            order.setStatus(Order.OrderStatus.PENDING);
            order.setPaymentStatus(Order.PaymentStatus.UNPAID);
            order.setOriginalFileName(file.getOriginalFilename());

            Order savedOrder = orderService.createOrder(order, items);

            // ⭐ ไม่ตัด Stock - ไม่ว่า autoDeductStock จะเป็นอะไร
            System.out.println("✓ Order saved: " + savedOrder.getOrderNumber());
            System.out.println("⚠️ Stock NOT deducted - manual deduction required");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "✅ อัพโหลด 24Shop สำเร็จ - กรุณาตัด Stock ด้วยตัวเอง",
                    "orderId", savedOrder.getOrderId(),
                    "orderNumber", savedOrder.getOrderNumber(),
                    "itemsCount", items.size(),
                    "parsedWith", "Gemini AI",
                    "note", "⚠️ ต้องตัด Stock ด้วยตัวเองในหน้ารายละเอียด Order"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Upload failed: " + e.getMessage())
            );
        }
    }

    @PostMapping("/upload/preview-24shop-pdf")
    public ResponseEntity<?> preview24ShopPDF(@RequestParam("file") MultipartFile file) {
        try {
            List<Map<String, Object>> items = geminiPDFParserService.parseAndPreview(file);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Preview generated successfully with Gemini AI",
                    "itemsCount", items.size(),
                    "items", items,
                    "parsedWith", "Gemini AI"
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Preview failed: " + e.getMessage())
            );
        }
    }

    // ============================================
    // Shopee Upload (เหมือนเดิม)
    // ============================================
    @PostMapping("/upload/shopee-excel")
    public ResponseEntity<?> uploadShopeeExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {
        try {
            System.out.println("========== Shopee Excel Upload Started ==========");

            // ⚠️ แจ้งเตือนถ้าผู้ใช้เปิด Auto Deduct
            if (autoDeductStock) {
                System.out.println("⚠️ WARNING: autoDeductStock is enabled but will be IGNORED");
                System.out.println("   All orders require MANUAL stock deduction");
            }

            List<Order> orders = excelParserService.parseShopeeExcel(file);
            int successCount = 0;
            int errorCount = 0;

            for (Order order : orders) {
                try {
                    List<OrderItem> items = new ArrayList<>(order.getOrderItems());
                    order.getOrderItems().clear();
                    Order savedOrder = orderService.createOrder(order, items);
                    successCount++;

                    // ⭐ ไม่ตัด Stock เลย
                    System.out.println("✓ Order saved: " + savedOrder.getOrderNumber());
                    System.out.println("⚠️ Stock NOT deducted - manual deduction required");
                } catch (Exception e) {
                    errorCount++;
                    System.err.println("❌ Failed to save order: " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "✅ อัพโหลด Shopee สำเร็จ - กรุณาตัด Stock ด้วยตัวเอง",
                    "totalOrders", orders.size(),
                    "successCount", successCount,
                    "errorCount", errorCount,
                    "note", "⚠️ ต้องตัด Stock ด้วยตัวเองในหน้ารายละเอียด Order"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Upload failed: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Order convertToOrder(OrderCreateRequest request) {
        Order order = new Order();
        order.setOrderNumber(request.getOrderNumber());
        if (request.getSource() != null) {
            order.setSource(Order.OrderSource.valueOf(request.getSource()));
        }
        order.setCustomerName(request.getCustomerName());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setShippingAddress(request.getShippingAddress());
        order.setOrderDate(request.getOrderDate() != null ? request.getOrderDate() : LocalDateTime.now());
        order.setDeliveryDate(request.getDeliveryDate());
        order.setShippingFee(request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO);
        order.setDiscount(request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO);
        order.setNotes(request.getNotes());
        return order;
    }

    private List<OrderItem> convertToOrderItems(List<OrderItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            return new ArrayList<>();
        }
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            OrderItem item = new OrderItem();
            item.setProductName(req.getProductName());
            item.setProductSku(req.getProductSku());
            item.setQuantity(req.getQuantity() != null ? req.getQuantity() : 1);
            item.setUnitPrice(req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO);
            item.setDiscount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO);
            item.setNotes(req.getNotes());
            if (req.getTotalPrice() != null) {
                item.setTotalPrice(req.getTotalPrice());
            }
            if (req.getProductId() != null) {
                Product product = new Product();
                product.setProductId(req.getProductId());
                item.setProduct(product);
            }
            item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
            items.add(item);
        }
        return items;
    }

    // ============================================
    // Response Classes
    // ============================================

    @lombok.Data
    static class ErrorResponse {
        private boolean success;
        private String message;
        public ErrorResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    @lombok.Data
    static class StockDeductionResponse {
        private boolean success;
        private String message;
        private List<String> messages;
        public StockDeductionResponse(boolean success, String message, List<String> messages) {
            this.success = success;
            this.message = message;
            this.messages = messages;
        }
    }

    @lombok.Data
    static class StockCheckResponse {
        private boolean available;
        private String message;
        private List<String> details;
        public StockCheckResponse(boolean available, String message, List<String> details) {
            this.available = available;
            this.message = message;
            this.details = details;
        }
    }

    @lombok.Data
    static class PreviewResponse {
        private boolean success;
        private String message;
        private OrderDTO order;
        private List<OrderDTO> orders;
    }

    @lombok.Data
    static class StatusUpdateRequest {
        private String status;
    }

    @lombok.Data
    static class PaymentStatusUpdateRequest {
        private String paymentStatus;
    }
}