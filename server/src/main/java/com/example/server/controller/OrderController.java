//package com.example.server.controller;
//
//import com.example.server.dto.*;
//import com.example.server.entity.Customer;
//import com.example.server.entity.Order;
//import com.example.server.entity.OrderItem;
//import com.example.server.entity.Product;
//import com.example.server.mapper.OrderMapper;
//import com.example.server.respository.CustomerRepository;
//import com.example.server.respository.OrderItemRepository;
//import com.example.server.respository.OrderRepository;
//import com.example.server.respository.ProductRepository;
//import com.example.server.service.*;
//import jakarta.transaction.Transactional;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.stream.Collectors;
//
//@RestController
//@RequestMapping("/api/orders")
//@CrossOrigin(origins = "*")
//public class OrderController {
//
//    @Autowired
//    private OrderService orderService;
//
//    @Autowired
//    private PDFParserService pdfParserService;
//
//    @Autowired
//    private ExcelParserService excelParserService;
//
//    @Autowired
//    private StockDeductionService stockDeductionService;
//
//    @Autowired
//    private OrderMapper orderMapper;
//    @Autowired
//    private CustomerRepository customerRepository;
//
//    @Autowired
//    private OrderItemRepository orderItemRepository;
//
//    @Autowired
//    private ProductRepository productRepository;
//
//    @Autowired
//    private OrderRepository orderRepository;
//    @GetMapping
//    public ResponseEntity<List<OrderDTO>> getAllOrders() {
//        List<Order> orders = orderService.getAllOrders();
//        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
//    }
//
//    @GetMapping("/{id}")
//    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long id) {
//        Optional<Order> order = orderService.getOrderById(id);
//        return order.map(o -> ResponseEntity.ok(orderMapper.toOrderDTO(o)))
//                .orElse(ResponseEntity.notFound().build());
//    }
//
//    @GetMapping("/number/{orderNumber}")
//    public ResponseEntity<OrderDTO> getOrderByNumber(@PathVariable String orderNumber) {
//        Optional<Order> order = orderService.getOrderByOrderNumber(orderNumber);
//        return order.map(o -> ResponseEntity.ok(orderMapper.toOrderDTO(o)))
//                .orElse(ResponseEntity.notFound().build());
//    }
//
//    @GetMapping("/status/{status}")
//    public ResponseEntity<List<OrderDTO>> getOrdersByStatus(@PathVariable String status) {
//        Order.OrderStatus orderStatus = Order.OrderStatus.valueOf(status);
//        List<Order> orders = orderService.getOrdersByStatus(orderStatus);
//        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
//    }
//
//    @GetMapping("/source/{source}")
//    public ResponseEntity<List<OrderDTO>> getOrdersBySource(@PathVariable String source) {
//        Order.OrderSource orderSource = Order.OrderSource.valueOf(source);
//        List<Order> orders = orderService.getOrdersBySource(orderSource);
//        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
//    }
//
//    @GetMapping("/search")
//    public ResponseEntity<List<OrderDTO>> searchOrders(@RequestParam String keyword) {
//        List<Order> orders = orderService.searchOrders(keyword);
//        return ResponseEntity.ok(orderMapper.toOrderDTOList(orders));
//    }
//
//    @PostMapping
//    public ResponseEntity<?> createOrder(@RequestBody OrderCreateRequest request) {
//        try {
//            Order order = convertToOrder(request);
//            List<OrderItem> items = convertToOrderItems(request.getOrderItems());
//
//            Order savedOrder = orderService.createOrder(order, items);
//            return ResponseEntity.ok(orderMapper.toOrderDTO(savedOrder));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to create order: " + e.getMessage())
//            );
//        }
//    }
//
//    @PutMapping("/{id}")
//    @Transactional
//    public ResponseEntity<?> updateOrder(@PathVariable Long id, @RequestBody OrderCreateRequest request) {
//        try {
//            // Convert request to Order entity
//            Order orderDetails = convertToOrder(request);
//
//            // Convert request items to OrderItem entities
//            List<OrderItem> newItems = null;
//            if (request.getOrderItems() != null && !request.getOrderItems().isEmpty()) {
//                newItems = convertToOrderItems(request.getOrderItems());
//            }
//
//            // ✅ เรียกใช้ method ใหม่ที่รับ items
//            Order updatedOrder = orderService.updateOrder(id, orderDetails, newItems);
//
//            return ResponseEntity.ok(orderMapper.toOrderDTO(updatedOrder));
//
//        } catch (RuntimeException e) {
//            e.printStackTrace();
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to update order: " + e.getMessage())
//            );
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Unexpected error: " + e.getMessage())
//            );
//        }
//    }
//
//    @PatchMapping("/{id}/status")
//    public ResponseEntity<?> updateOrderStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
//        try {
//            Order.OrderStatus status = Order.OrderStatus.valueOf(request.getStatus());
//            Order updatedOrder = orderService.updateOrderStatus(id, status);
//            return ResponseEntity.ok(orderMapper.toOrderDTO(updatedOrder));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to update status: " + e.getMessage())
//            );
//        }
//    }
//
////    @PatchMapping("/{id}/payment-status")
////    public ResponseEntity<?> updatePaymentStatus(
////            @PathVariable Long id,
////            @RequestBody Map<String, String> request) {
////        try {
////            String paymentStatus = request.get("paymentStatus");
////
////            if (paymentStatus == null || paymentStatus.isEmpty()) {
////                return ResponseEntity.badRequest().body(Map.of(
////                        "success", false,
////                        "message", "Payment status is required"
////                ));
////            }
////
////            Order updatedOrder = orderService.updatePaymentStatus(id, Order.PaymentStatus.valueOf(paymentStatus));
////            return ResponseEntity.ok(updatedOrder);
////
////        } catch (IllegalArgumentException e) {
////            return ResponseEntity.badRequest().body(Map.of(
////                    "success", false,
////                    "message", e.getMessage()
////            ));
////        } catch (Exception e) {
////            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
////                    "success", false,
////                    "message", "Failed to update payment status: " + e.getMessage()
////            ));
////        }
////    }
//    @PatchMapping("/{id}/payment-status")
//    public ResponseEntity<?> updatePaymentStatus(
//            @PathVariable Long id,
//            @RequestBody Map<String, String> request) {
//        try {
//            String paymentStatus = request.get("paymentStatus");
//
//            if (paymentStatus == null || paymentStatus.isEmpty()) {
//                return ResponseEntity.badRequest().body(Map.of(
//                        "success", false,
//                        "message", "Payment status is required"
//                ));
//            }
//
//            Order updatedOrder = orderService.updatePaymentStatus(id, Order.PaymentStatus.valueOf(paymentStatus));
//
//            // ✅ แปลง Entity เป็น DTO ก่อน return
//            OrderDTO orderDTO = orderMapper.toOrderDTO(updatedOrder);
//            return ResponseEntity.ok(orderDTO);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
//                    "success", false,
//                    "message", "Failed to update payment status: " + e.getMessage()
//            ));
//        }
//    }
//    @PostMapping("/{id}/cancel")
//    public ResponseEntity<?> cancelOrder(@PathVariable Long id) {
//        try {
//            Order cancelledOrder = orderService.cancelOrder(id);
//            return ResponseEntity.ok(orderMapper.toOrderDTO(cancelledOrder));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to cancel order: " + e.getMessage())
//            );
//        }
//    }
//
//    @DeleteMapping("/{id}")
//    public ResponseEntity<?> deleteOrder(@PathVariable Long id) {
//        try {
//            orderService.deleteOrder(id);
//            return ResponseEntity.ok(new ErrorResponse(true, "Order deleted successfully"));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to delete order: " + e.getMessage())
//            );
//        }
//    }
//
//    @PostMapping("/{id}/deduct-stock")
//    public ResponseEntity<?> deductStockForOrder(@PathVariable Long id) {
//        try {
//            Order order = orderService.getOrderById(id)
//                    .orElseThrow(() -> new RuntimeException("Order not found"));
//
//            List<String> messages = stockDeductionService.deductStockForOrder(order);
//
//            return ResponseEntity.ok(new StockDeductionResponse(true, "Stock deduction completed", messages));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to deduct stock: " + e.getMessage())
//            );
//        }
//    }
//
//    @GetMapping("/{id}/check-stock")
//    public ResponseEntity<?> checkStockAvailability(@PathVariable Long id) {
//        try {
//            Order order = orderService.getOrderById(id)
//                    .orElseThrow(() -> new RuntimeException("Order not found"));
//
//            boolean allAvailable = true;
//            List<String> messages = new ArrayList<>();
//
//            for (OrderItem item : order.getOrderItems()) {
//                boolean available = stockDeductionService.checkStockAvailability(item);
//                if (!available) {
//                    allAvailable = false;
//                    messages.add("✗ " + item.getProductName() + " - Stock ไม่เพียงพอ");
//                } else {
//                    messages.add("✓ " + item.getProductName() + " - Stock เพียงพอ");
//                }
//            }
//
//            String message = allAvailable ? "Stock เพียงพอทั้งหมด" : "Stock ไม่เพียงพอบางรายการ";
//            return ResponseEntity.ok(new StockCheckResponse(allAvailable, message, messages));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    new ErrorResponse(false, "Failed to check stock: " + e.getMessage())
//            );
//        }
//    }
//
//    @PostMapping("/upload/24shop-pdf")
//    public ResponseEntity<?> upload24ShopPDF(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("orderNumber") String orderNumber,
//            @RequestParam("customerId") Long customerId,
//            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {
//        try {
//            // 1. ตรวจสอบว่าเป็นไฟล์ PDF
//            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
//                return ResponseEntity.badRequest().body(
//                        Map.of("success", false, "message", "ไฟล์ต้องเป็น PDF เท่านั้น")
//                );
//            }
//
//            // 2. ตรวจสอบว่า Customer มีอยู่จริง
//            Customer customer = customerRepository.findById(customerId)
//                    .orElseThrow(() -> new RuntimeException("ไม่พบข้อมูลลูกค้า ID: " + customerId));
//
//            // 3. Parse เฉพาะรายการสินค้าจาก PDF
//            List<OrderItem> items = pdfParserService.parseOrderItemsFromPDF(file);
//
//            if (items.isEmpty()) {
//                return ResponseEntity.badRequest().body(
//                        Map.of("success", false, "message", "ไม่พบรายการสินค้าใน PDF")
//                );
//            }
//
//            // 4. สร้าง Order พร้อมข้อมูลลูกค้า
//            Order order = new Order();
//            order.setOrderNumber(orderNumber);
//            order.setSource(Order.OrderSource.SHOP_24);
//            order.setCustomer(customer);
//            order.setCustomerName(customer.getCustomerName());
//            order.setCustomerPhone(customer.getCustomerPhone()); // หรือ getContactPhone()
//            order.setShippingAddress(customer.getCustomerAddress()); // หรือ getContactAddress()
//            order.setOrderDate(LocalDateTime.now());
//            order.setStatus(Order.OrderStatus.PENDING);
//            order.setPaymentStatus(Order.PaymentStatus.UNPAID);
//            order.setOriginalFileName(file.getOriginalFilename());
//
//            // 5. บันทึก Order
//            Order savedOrder = orderService.createOrder(order, items);
//
//            // 6. ตัด Stock ถ้าต้องการ
//            List<String> stockMessages = new ArrayList<>();
//            if (autoDeductStock) {
//                stockMessages = stockDeductionService.deductStockForOrder(savedOrder);
//            }
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true,
//                    "message", "Upload successful",
//                    "orderId", savedOrder.getOrderId(),
//                    "orderNumber", savedOrder.getOrderNumber(),
//                    "itemsCount", items.size(),
//                    "stockDeductionMessages", stockMessages
//            ));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.badRequest().body(
//                    Map.of("success", false, "message", "Upload failed: " + e.getMessage())
//            );
//        }
//    }
//
//    @PostMapping("/upload/shopee-excel")
//    public ResponseEntity<?> uploadShopeeExcel(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {
//        try {
//            List<Order> orders = excelParserService.parseShopeeExcel(file);
//
//            int successCount = 0;
//            int errorCount = 0;
//            List<String> allStockMessages = new ArrayList<>();
//
//            for (Order order : orders) {
//                try {
//                    List<OrderItem> items = new ArrayList<>(order.getOrderItems());
//                    order.getOrderItems().clear();
//
//                    Order savedOrder = orderService.createOrder(order, items);
//                    successCount++;
//
//                    if (autoDeductStock) {
//                        List<String> stockMessages = stockDeductionService.deductStockForOrder(savedOrder);
//                        allStockMessages.addAll(stockMessages);
//                    }
//                } catch (Exception e) {
//                    errorCount++;
//                }
//            }
//
//            OrderUploadResponse response = new OrderUploadResponse();
//            response.setSuccess(true);
//            response.setMessage(successCount + " orders uploaded successfully");
//            response.setStockDeductionMessages(allStockMessages);
//
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            OrderUploadResponse response = new OrderUploadResponse();
//            response.setSuccess(false);
//            response.setMessage("Upload failed: " + e.getMessage());
//            return ResponseEntity.badRequest().body(response);
//        }
//    }
//
//    @PostMapping("/upload/preview-24shop-pdf")
//    public ResponseEntity<?> preview24ShopPDF(@RequestParam("file") MultipartFile file) {
//        try {
//            // Parse เฉพาะรายการสินค้า
//            List<OrderItem> items = pdfParserService.parseOrderItemsFromPDF(file);
//
//            return ResponseEntity.ok(Map.of(
//                    "success", true,
//                    "message", "Preview generated successfully",
//                    "itemsCount", items.size(),
//                    "items", items.stream().map(item -> Map.of(
//                            "productSku", item.getProductSku(),
//                            "productName", item.getProductName(),
//                            "quantity", item.getQuantity(),
//                            "unitPrice", item.getUnitPrice(),
//                            "totalPrice", item.getTotalPrice()
//                    )).collect(Collectors.toList())
//            ));
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(
//                    Map.of("success", false, "message", "Preview failed: " + e.getMessage())
//            );
//        }
//    }
//
//    @PostMapping("/upload/preview-excel")
//    public ResponseEntity<?> previewExcel(@RequestParam("file") MultipartFile file) {
//        try {
//            List<Order> orders = excelParserService.parseShopeeExcel(file);
//
//            PreviewResponse response = new PreviewResponse();
//            response.setSuccess(true);
//            response.setMessage("Preview generated successfully");
//            response.setOrders(orderMapper.toOrderDTOList(orders));
//
//            return ResponseEntity.ok(response);
//        } catch (Exception e) {
//            PreviewResponse response = new PreviewResponse();
//            response.setSuccess(false);
//            response.setMessage("Preview failed: " + e.getMessage());
//            return ResponseEntity.badRequest().body(response);
//        }
//    }
//
//    // ============================================
//    // Helper Methods
//    // ============================================
//
//    private Order convertToOrder(OrderCreateRequest request) {
//        Order order = new Order();
//        order.setOrderNumber(request.getOrderNumber());
//
//        if (request.getSource() != null) {
//            order.setSource(Order.OrderSource.valueOf(request.getSource()));
//        }
//
//        order.setCustomerName(request.getCustomerName());
//        order.setCustomerPhone(request.getCustomerPhone());
//        order.setShippingAddress(request.getShippingAddress());
//        order.setOrderDate(request.getOrderDate() != null ? request.getOrderDate() : LocalDateTime.now());
//        order.setDeliveryDate(request.getDeliveryDate());
//        order.setShippingFee(request.getShippingFee() != null ? request.getShippingFee() : BigDecimal.ZERO);
//        order.setDiscount(request.getDiscount() != null ? request.getDiscount() : BigDecimal.ZERO);
//        order.setNotes(request.getNotes());
//
//        return order;
//    }
//
//    private List<OrderItem> convertToOrderItems(List<OrderItemRequest> itemRequests) {
//        if (itemRequests == null || itemRequests.isEmpty()) {
//            return new ArrayList<>();
//        }
//
//        List<OrderItem> items = new ArrayList<>();
//
//        for (OrderItemRequest req : itemRequests) {
//            OrderItem item = new OrderItem();
//
//            // Basic info
//            item.setProductName(req.getProductName());
//            item.setProductSku(req.getProductSku());
//            item.setQuantity(req.getQuantity() != null ? req.getQuantity() : 1);
//            item.setUnitPrice(req.getUnitPrice() != null ? req.getUnitPrice() : BigDecimal.ZERO);
//            item.setDiscount(req.getDiscount() != null ? req.getDiscount() : BigDecimal.ZERO);
//            item.setNotes(req.getNotes());
//
//            // ⭐ ถ้ามี totalPrice ส่งมา ให้ใช้เลย (จาก Frontend)
//            if (req.getTotalPrice() != null) {
//                item.setTotalPrice(req.getTotalPrice());
//            }
//
//            // Set product reference if productId exists
//            if (req.getProductId() != null) {
//                Product product = new Product();
//                product.setProductId(req.getProductId());
//                item.setProduct(product);
//            }
//
//            item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
//
//            items.add(item);
//        }
//
//        return items;
//    }
//
//
//    @lombok.Data
//    static class ErrorResponse {
//        private boolean success;
//        private String message;
//
//        public ErrorResponse(boolean success, String message) {
//            this.success = success;
//            this.message = message;
//        }
//    }
//
//    @lombok.Data
//    static class StockDeductionResponse {
//        private boolean success;
//        private String message;
//        private List<String> messages;
//
//        public StockDeductionResponse(boolean success, String message, List<String> messages) {
//            this.success = success;
//            this.message = message;
//            this.messages = messages;
//        }
//    }
//
//    @lombok.Data
//    static class StockCheckResponse {
//        private boolean available;
//        private String message;
//        private List<String> details;
//
//        public StockCheckResponse(boolean available, String message, List<String> details) {
//            this.available = available;
//            this.message = message;
//            this.details = details;
//        }
//    }
//
//    @lombok.Data
//    static class PreviewResponse {
//        private boolean success;
//        private String message;
//        private OrderDTO order;
//        private List<OrderDTO> orders;
//    }
//
//    @lombok.Data
//    static class StatusUpdateRequest {
//        private String status;
//    }
//
//    @lombok.Data
//    static class PaymentStatusUpdateRequest {
//        private String paymentStatus;
//    }
//}
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
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    @Autowired
    private OrderService orderService;

    // ⭐ เปลี่ยนจาก PDFParserService เป็น GeminiPDFParserService
    @Autowired
    private GeminiPDFParserService geminiPDFParserService;

    @Autowired
    private ExcelParserService excelParserService;

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

    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderCreateRequest request) {
        try {
            Order order = convertToOrder(request);
            List<OrderItem> items = convertToOrderItems(request.getOrderItems());

            Order savedOrder = orderService.createOrder(order, items);
            return ResponseEntity.ok(orderMapper.toOrderDTO(savedOrder));
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

    /**
     * ⭐ อัพเดท: ใช้ Gemini AI แทน PDFBox
     */
    @PostMapping("/upload/24shop-pdf")
    public ResponseEntity<?> upload24ShopPDF(
            @RequestParam("file") MultipartFile file,
            @RequestParam("orderNumber") String orderNumber,
            @RequestParam("customerId") Long customerId,
            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {
        try {
            // Validate file type
            if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "ไฟล์ต้องเป็น PDF เท่านั้น")
                );
            }

            // Validate customer
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new RuntimeException("ไม่พบข้อมูลลูกค้า ID: " + customerId));

            // ⭐ ใช้ Gemini AI ในการ parse
            List<OrderItem> items = geminiPDFParserService.parseOrderItemsFromPDF(file);

            if (items.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        Map.of("success", false, "message", "ไม่พบรายการสินค้าใน PDF หรือ Gemini ไม่สามารถอ่านได้")
                );
            }

            // Create order
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

            // Save order
            Order savedOrder = orderService.createOrder(order, items);

            // Deduct stock if requested
            List<String> stockMessages = new ArrayList<>();
            if (autoDeductStock) {
                stockMessages = stockDeductionService.deductStockForOrder(savedOrder);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "✅ Upload successful with Gemini AI",
                    "orderId", savedOrder.getOrderId(),
                    "orderNumber", savedOrder.getOrderNumber(),
                    "itemsCount", items.size(),
                    "stockDeductionMessages", stockMessages,
                    "parsedWith", "Gemini AI"
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Upload failed: " + e.getMessage())
            );
        }
    }

    /**
     * ⭐ อัพเดท: Preview ด้วย Gemini AI
     */
    @PostMapping("/upload/preview-24shop-pdf")
    public ResponseEntity<?> preview24ShopPDF(@RequestParam("file") MultipartFile file) {
        try {
            // ⭐ ใช้ Gemini AI
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

    @PostMapping("/upload/shopee-excel")
    public ResponseEntity<?> uploadShopeeExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "autoDeductStock", defaultValue = "false") boolean autoDeductStock) {
        try {
            List<Order> orders = excelParserService.parseShopeeExcel(file);
            int successCount = 0;
            int errorCount = 0;
            List<String> allStockMessages = new ArrayList<>();

            for (Order order : orders) {
                try {
                    List<OrderItem> items = new ArrayList<>(order.getOrderItems());
                    order.getOrderItems().clear();
                    Order savedOrder = orderService.createOrder(order, items);
                    successCount++;
                    if (autoDeductStock) {
                        List<String> stockMessages = stockDeductionService.deductStockForOrder(savedOrder);
                        allStockMessages.addAll(stockMessages);
                    }
                } catch (Exception e) {
                    errorCount++;
                }
            }

            OrderUploadResponse response = new OrderUploadResponse();
            response.setSuccess(true);
            response.setMessage(successCount + " orders uploaded successfully");
            response.setStockDeductionMessages(allStockMessages);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            OrderUploadResponse response = new OrderUploadResponse();
            response.setSuccess(false);
            response.setMessage("Upload failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/upload/preview-excel")
    public ResponseEntity<?> previewExcel(@RequestParam("file") MultipartFile file) {
        try {
            List<Order> orders = excelParserService.parseShopeeExcel(file);
            PreviewResponse response = new PreviewResponse();
            response.setSuccess(true);
            response.setMessage("Preview generated successfully");
            response.setOrders(orderMapper.toOrderDTOList(orders));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            PreviewResponse response = new PreviewResponse();
            response.setSuccess(false);
            response.setMessage("Preview failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // Helper Methods
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