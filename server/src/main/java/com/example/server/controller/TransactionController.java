package com.example.server.controller;

import com.example.server.dto.*;
import com.example.server.entity.Order;
import com.example.server.entity.Transaction;
import com.example.server.respository.OrderRepository;
import com.example.server.respository.StockLotRepository;
import com.example.server.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.server.entity.ChinaStock;
import com.example.server.entity.ThaiStock;
import com.example.server.entity.StockBase;
import com.example.server.entity.StockLot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StockLotRepository stockLotRepository;

    // ============================================
    // GET Endpoints
    // ============================================

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getAllTransactions() {
        List<Transaction> transactions = transactionService.getAllTransactions();
        List<TransactionDTO> dtos = transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable Long id) {
        Optional<Transaction> transaction = transactionService.getTransactionById(id);
        return transaction.map(t -> ResponseEntity.ok(toDTO(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByType(@PathVariable String type) {
        Transaction.TransactionType transactionType = Transaction.TransactionType.valueOf(type.toUpperCase());
        List<Transaction> transactions = transactionService.getTransactionsByType(transactionType);
        List<TransactionDTO> dtos = transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByCategory(@PathVariable String category) {
        Transaction.TransactionCategory transactionCategory = Transaction.TransactionCategory.valueOf(category.toUpperCase());
        List<Transaction> transactions = transactionService.getTransactionsByCategory(transactionCategory);
        List<TransactionDTO> dtos = transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<TransactionDTO>> getTransactionsByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<Transaction> transactions = transactionService.getTransactionsByDateRange(startDate, endDate);
        List<TransactionDTO> dtos = transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<TransactionDTO>> searchTransactions(@RequestParam String keyword) {
        List<Transaction> transactions = transactionService.searchTransactions(keyword);
        List<TransactionDTO> dtos = transactions.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // ============================================
    // Summary & Reports
    // ============================================

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getTransactionSummary() {
        Map<String, Object> summary = transactionService.getTransactionSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * ⭐ NEW: รายงานรายเดือน (สำหรับ Frontend)
     * GET /api/transactions/reports/monthly?year=2025&month=11
     */
    @GetMapping("/reports/monthly")
    public ResponseEntity<?> getMonthlyReport(
            @RequestParam int year,
            @RequestParam int month) {
        try {
            if (month < 1 || month > 12) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Month must be between 1 and 12"
                ));
            }

            MonthlyReportResponse report = transactionService.getMonthlyReport(year, month);
            return ResponseEntity.ok(report);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error generating monthly report: " + e.getMessage()
            ));
        }
    }

    /**
     * ⭐ NEW: รายงานทั้งปี (สำหรับ Frontend)
     * GET /api/transactions/reports/yearly/2025
     */
    @GetMapping("/reports/yearly/{year}")
    public ResponseEntity<?> getYearlyReport(@PathVariable int year) {
        try {
            if (year < 2000 || year > 2100) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Invalid year"
                ));
            }

            List<MonthlyReportResponse> reports = transactionService.getYearlyReport(year);
            return ResponseEntity.ok(reports);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Error generating yearly report: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // POST, PUT, DELETE (Manual Transactions)
    // ============================================

    @PostMapping
    public ResponseEntity<?> createManualTransaction(
            @RequestBody TransactionCreateRequest request,
            @RequestParam(required = false, defaultValue = "admin") String username) {
        try {
            Transaction transaction = toEntity(request);
            Transaction savedTransaction = transactionService.createManualTransaction(transaction, username);
            return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(savedTransaction));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(
            @PathVariable Long id,
            @RequestBody TransactionCreateRequest request) {
        try {
            Transaction details = toEntity(request);
            Transaction updatedTransaction = transactionService.updateTransaction(id, details);
            return ResponseEntity.ok(toDTO(updatedTransaction));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTransaction(@PathVariable Long id) {
        try {
            transactionService.deleteTransaction(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Transaction deleted successfully"
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * ✅ สร้าง Transaction อัตโนมัติจาก Order
     * POST /api/transactions/auto/order/{orderId}
     */
    @PostMapping("/auto/order/{orderId}")
    public ResponseEntity<?> createAutoTransactionForOrder(@PathVariable Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

            Transaction transaction = transactionService.createOrderPaymentTransaction(order);

            TransactionDTO dto = toDTO(transaction);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to create auto transaction: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ สร้าง Transaction อัตโนมัติจาก Stock Lot
     * POST /api/transactions/auto/stock-lot/{stockLotId}
     */
    @PostMapping("/auto/stock-lot/{stockLotId}")
    public ResponseEntity<?> createAutoTransactionForStockLot(@PathVariable Long stockLotId) {
        try {
            StockLot stockLot = stockLotRepository.findById(stockLotId)
                    .orElseThrow(() -> new RuntimeException("Stock lot not found with id: " + stockLotId));

            // คำนวณต้นทุนรวม
            BigDecimal totalCost = calculateStockLotCost(stockLot);

            if (totalCost.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Cannot create transaction: Stock lot has no cost"
                ));
            }

            Transaction transaction = transactionService.createStockPurchaseTransaction(stockLot, totalCost);

            TransactionDTO dto = toDTO(transaction);
            return ResponseEntity.status(HttpStatus.CREATED).body(dto);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Failed to create auto transaction: " + e.getMessage()
            ));
        }
    }

    /**
     * Helper method สำหรับคำนวณต้นทุนของ Stock Lot
     */
    private BigDecimal calculateStockLotCost(StockLot stockLot) {
        BigDecimal total = BigDecimal.ZERO;

        if (stockLot.getItems() == null || stockLot.getItems().isEmpty()) {
            return total;
        }

        for (StockBase item : stockLot.getItems()) {
            if (item instanceof ChinaStock) {
                ChinaStock china = (ChinaStock) item;
                if (china.getTotalBath() != null) {
                    total = total.add(china.getTotalBath());
                }
            } else if (item instanceof ThaiStock) {
                ThaiStock thai = (ThaiStock) item;
                BigDecimal itemTotal = thai.calculateTotalCost();
                if (itemTotal != null) {
                    total = total.add(itemTotal);
                }
            }
        }

        return total;
    }

    // ============================================
    // Helper Methods - DTO Conversion
    // ============================================

    private TransactionDTO toDTO(Transaction transaction) {
        TransactionDTO dto = new TransactionDTO();
        dto.setTransactionId(transaction.getTransactionId());
        dto.setType(transaction.getType().name());
        dto.setCategory(transaction.getCategory().name());
        dto.setAmount(transaction.getAmount());
        dto.setDescription(transaction.getDescription());
        dto.setOrderId(transaction.getOrderId());
        dto.setStockLotId(transaction.getStockLotId());
        dto.setEmployeeId(transaction.getEmployeeId());
        dto.setSalaryPaymentId(transaction.getSalaryPaymentId());
        dto.setTransactionDate(transaction.getTransactionDate());
        dto.setMode(transaction.getMode().name());
        dto.setCreatedBy(transaction.getCreatedBy());
        dto.setNotes(transaction.getNotes());
        dto.setCreatedDate(transaction.getCreatedDate());
        dto.setUpdatedDate(transaction.getUpdatedDate());
        return dto;
    }

    private Transaction toEntity(TransactionCreateRequest request) {
        Transaction transaction = new Transaction();
        transaction.setType(Transaction.TransactionType.valueOf(request.getType().toUpperCase()));
        transaction.setCategory(Transaction.TransactionCategory.valueOf(request.getCategory().toUpperCase()));
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setOrderId(request.getOrderId());
        transaction.setStockLotId(request.getStockLotId());
        transaction.setEmployeeId(request.getEmployeeId());
        transaction.setTransactionDate(request.getTransactionDate());
        transaction.setNotes(request.getNotes());
        return transaction;
    }
}