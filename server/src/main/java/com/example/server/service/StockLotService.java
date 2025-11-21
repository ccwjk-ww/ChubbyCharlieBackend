package com.example.server.service;

import com.example.server.dto.StockLotSummaryDTO;
import com.example.server.dto.SystemSummaryDTO;
import com.example.server.entity.ChinaStock;
import com.example.server.entity.StockBase;
import com.example.server.entity.StockLot;
import com.example.server.entity.ThaiStock;
import com.example.server.respository.StockLotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class StockLotService {

    @Autowired
    private StockLotRepository stockLotRepository;

    @Autowired
    private TransactionService transactionService;

    @Transactional(readOnly = true)
    public List<StockLot> getAllStockLots() {
        try {
            List<StockLot> lots = stockLotRepository.findAll();

            // Force initialization ของ lazy collections
            lots.forEach(lot -> {
                if (lot.getItems() != null) {
                    lot.getItems().size(); // Force loading
                }
            });

            return lots;
        } catch (Exception e) {
            throw new RuntimeException("Error loading stock lots: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<StockLot> getStockLotById(Long id) {
        try {
            Optional<StockLot> lot = stockLotRepository.findById(id);

            // Force initialization
            lot.ifPresent(stockLot -> {
                if (stockLot.getItems() != null) {
                    stockLot.getItems().size();
                }
            });

            return lot;
        } catch (Exception e) {
            throw new RuntimeException("Error loading stock lot with id: " + id, e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<StockLot> getStockLotByName(String lotName) {
        try {
            Optional<StockLot> lot = stockLotRepository.findByLotName(lotName);

            // Force initialization
            lot.ifPresent(stockLot -> {
                if (stockLot.getItems() != null) {
                    stockLot.getItems().size();
                }
            });

            return lot;
        } catch (Exception e) {
            throw new RuntimeException("Error loading stock lot with name: " + lotName, e);
        }
    }

    @Transactional(readOnly = true)
    public List<StockLot> getStockLotsByStatus(StockLot.StockStatus status) {
        try {
            List<StockLot> lots = stockLotRepository.findByStatus(status);

            // Force initialization
            lots.forEach(lot -> {
                if (lot.getItems() != null) {
                    lot.getItems().size();
                }
            });

            return lots;
        } catch (Exception e) {
            throw new RuntimeException("Error loading stock lots by status: " + status, e);
        }
    }

    /**
     * ✅ สร้าง StockLot (ไม่สร้าง Transaction ทันที)
     * Transaction จะถูกสร้างเมื่อเรียก completeStockLot()
     */
    public StockLot createStockLot(StockLot stockLot) {
        try {
            // Validation
            if (stockLot.getLotName() == null || stockLot.getLotName().trim().isEmpty()) {
                throw new IllegalArgumentException("Lot name is required");
            }

            // Check if lot name already exists
            if (stockLotRepository.findByLotName(stockLot.getLotName()).isPresent()) {
                throw new IllegalArgumentException("Lot name already exists: " + stockLot.getLotName());
            }

            // Set default values
            if (stockLot.getStatus() == null) {
                stockLot.setStatus(StockLot.StockStatus.PENDING);
            }
            if (stockLot.getImportDate() == null) {
                stockLot.setImportDate(LocalDateTime.now());
            }

            // ✅ แค่ save - ไม่สร้าง Transaction ตอนนี้
            return stockLotRepository.save(stockLot);

        } catch (Exception e) {
            throw new RuntimeException("Error creating stock lot: " + e.getMessage(), e);
        }
    }

    /**
     * ⭐ NEW: Complete Stock Lot และสร้าง Transaction อัตโนมัติ
     * เรียกใช้เมื่อผู้ใช้คลิกปุ่ม "Complete & Create Transaction"
     */
    @Transactional
    public StockLot completeStockLot(Long id) {
        try {
            StockLot stockLot = stockLotRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Stock lot not found with id: " + id));

            // ตรวจสอบสถานะ - ต้องไม่เป็น COMPLETED อยู่แล้ว
            if (stockLot.getStatus() == StockLot.StockStatus.COMPLETED) {
                throw new IllegalStateException("Stock lot is already completed");
            }

            // ตรวจสอบว่ามีสินค้าหรือไม่
            if (stockLot.getItems() == null || stockLot.getItems().isEmpty()) {
                throw new IllegalStateException("Cannot complete: Stock lot has no items");
            }

            // คำนวณยอดรวม
            BigDecimal totalCost = calculateTotalCost(stockLot);

            if (totalCost.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Cannot complete: Total cost must be greater than zero");
            }

            // เปลี่ยนสถานะเป็น COMPLETED
            stockLot.setStatus(StockLot.StockStatus.COMPLETED);
            StockLot savedLot = stockLotRepository.save(stockLot);

            // ✅ สร้าง Transaction
            try {
                transactionService.createStockPurchaseTransaction(savedLot, totalCost);
            } catch (Exception e) {
                // ถ้าสร้าง Transaction ล้มเหลว ให้ rollback status
                System.err.println("Failed to create transaction: " + e.getMessage());
                stockLot.setStatus(StockLot.StockStatus.ARRIVED);
                stockLotRepository.save(stockLot);
                throw new RuntimeException("Failed to create transaction: " + e.getMessage());
            }

            return savedLot;

        } catch (Exception e) {
            throw new RuntimeException("Error completing stock lot: " + e.getMessage(), e);
        }
    }

    public StockLot updateStockLot(Long id, StockLot stockLotDetails) {
        try {
            StockLot stockLot = stockLotRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Stock lot not found with id: " + id));

            // ป้องกันการแก้ไขถ้า COMPLETED แล้ว
            if (stockLot.getStatus() == StockLot.StockStatus.COMPLETED) {
                throw new IllegalStateException("Cannot edit completed stock lot");
            }

            // Update fields
            if (stockLotDetails.getLotName() != null) {
                // Check if new lot name already exists (excluding current lot)
                Optional<StockLot> existingLot = stockLotRepository.findByLotName(stockLotDetails.getLotName());
                if (existingLot.isPresent() && !existingLot.get().getStockLotId().equals(id)) {
                    throw new IllegalArgumentException("Lot name already exists: " + stockLotDetails.getLotName());
                }
                stockLot.setLotName(stockLotDetails.getLotName());
            }
            if (stockLotDetails.getImportDate() != null) {
                stockLot.setImportDate(stockLotDetails.getImportDate());
            }
            if (stockLotDetails.getArrivalDate() != null) {
                stockLot.setArrivalDate(stockLotDetails.getArrivalDate());
            }
            if (stockLotDetails.getStatus() != null) {
                stockLot.setStatus(stockLotDetails.getStatus());
            }

            return stockLotRepository.save(stockLot);
        } catch (Exception e) {
            throw new RuntimeException("Error updating stock lot: " + e.getMessage(), e);
        }
    }

    public StockLot updateStockLotStatus(Long id, StockLot.StockStatus status) {
        try {
            StockLot stockLot = stockLotRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Stock lot not found with id: " + id));

            stockLot.setStatus(status);
            return stockLotRepository.save(stockLot);
        } catch (Exception e) {
            throw new RuntimeException("Error updating stock lot status: " + e.getMessage(), e);
        }
    }

    public void deleteStockLot(Long id) {
        try {
            StockLot stockLot = stockLotRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Stock lot not found"));

            // ป้องกันการลบถ้า COMPLETED แล้ว
            if (stockLot.getStatus() == StockLot.StockStatus.COMPLETED) {
                throw new IllegalStateException("Cannot delete completed stock lot");
            }

            stockLotRepository.deleteById(id);
        } catch (Exception e) {
            throw new RuntimeException("Error deleting stock lot: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<StockLot> getStockLotsByDateRange(LocalDateTime startDate, LocalDateTime endDate, String dateType) {
        try {
            List<StockLot> lots;
            if ("arrival".equalsIgnoreCase(dateType)) {
                lots = stockLotRepository.findByArrivalDateBetween(startDate, endDate);
            } else {
                lots = stockLotRepository.findByImportDateBetween(startDate, endDate);
            }

            // Force initialization
            lots.forEach(lot -> {
                if (lot.getItems() != null) {
                    lot.getItems().size();
                }
            });

            return lots;
        } catch (Exception e) {
            throw new RuntimeException("Error loading stock lots by date range: " + e.getMessage(), e);
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

//    /**
//     * ⭐ คำนวณยอดรวมของ StockLot จาก items ทั้งหมด
//     */
//    public BigDecimal calculateTotalCost(StockLot stockLot) {
//        BigDecimal total = BigDecimal.ZERO;
//
//        if (stockLot.getItems() == null || stockLot.getItems().isEmpty()) {
//            return total;
//        }
//
//        for (StockBase item : stockLot.getItems()) {
//            if (item instanceof ChinaStock) {
//                ChinaStock china = (ChinaStock) item;
//                if (china.getTotalBath() != null) {
//                    total = total.add(china.getTotalBath());
//                }
//            } else if (item instanceof ThaiStock) {
//                ThaiStock thai = (ThaiStock) item;
//                BigDecimal itemTotal = thai.calculateTotalCost();
//                if (itemTotal != null) {
//                    total = total.add(itemTotal);
//                }
//            }
//        }
//
//        return total;
//    }
    /**
     * ⭐ คำนวณยอดรวมของ StockLot รวมค่าส่งจากจีนมาไทย
     */
    public BigDecimal calculateTotalCost(StockLot stockLot) {
        BigDecimal total = BigDecimal.ZERO;

        if (stockLot.getItems() == null || stockLot.getItems().isEmpty()) {
            return total;
        }

        for (StockBase item : stockLot.getItems()) {
            if (item instanceof ChinaStock) {
                ChinaStock china = (ChinaStock) item;
                // ⭐ ใช้ calculateTotalCost() ที่รวมค่าส่งแล้ว
                BigDecimal itemTotal = china.calculateTotalCost();
                if (itemTotal != null) {
                    total = total.add(itemTotal);
                }
            } else if (item instanceof ThaiStock) {
                ThaiStock thai = (ThaiStock) item;
                BigDecimal itemTotal = thai.calculateTotalCost();
                if (itemTotal != null) {
                    total = total.add(itemTotal);
                }
            }
        }

        return total.setScale(3, RoundingMode.HALF_UP);
    }
    /**
     * ⭐ แก้ไข: คำนวณ Total Value ให้รวม Buffer
     */
    @Transactional(readOnly = true)
    public StockLotSummaryDTO getStockLotSummary(Long stockLotId) {
        StockLot stockLot = stockLotRepository.findById(stockLotId)
                .orElseThrow(() -> new RuntimeException("Stock Lot not found"));

        StockLotSummaryDTO summary = new StockLotSummaryDTO();
        summary.setStockLotId(stockLot.getStockLotId());
        summary.setLotName(stockLot.getLotName());

        List<StockBase> items = stockLot.getItems();
        if (items == null || items.isEmpty()) {
            summary.setTotalItemCount(0);
            summary.setChinaItemCount(0);
            summary.setThaiItemCount(0);
            summary.setGrandTotalValue(BigDecimal.ZERO);
            return summary;
        }

        // นับจำนวน items
        long chinaCount = items.stream()
                .filter(item -> item instanceof ChinaStock)
                .count();
        long thaiCount = items.stream()
                .filter(item -> item instanceof ThaiStock)
                .count();

                summary.setTotalItemCount(items.size());
                summary.setChinaItemCount((int) chinaCount);
                summary.setThaiItemCount((int) thaiCount);

        // ⭐ คำนวณ Grand Total Value (รวม Buffer)
        BigDecimal grandTotalValue = items.stream()
                .map(item -> {
                    if (item instanceof ChinaStock) {
                        ChinaStock chinaStock = (ChinaStock) item;
                        // ใช้ calculateTotalCost() ที่รวม Buffer แล้ว
                        return chinaStock.calculateTotalCost();
                    } else if (item instanceof ThaiStock) {
                        ThaiStock thaiStock = (ThaiStock) item;
                        // ใช้ calculateTotalCost() ที่รวม Buffer แล้ว
                        return thaiStock.calculateTotalCost();
                    }
                    return BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.setGrandTotalValue(grandTotalValue);

        return summary;
    }

    /**
     * ⭐ แก้ไข: System Summary ให้ใช้ Grand Total
     */
    @Transactional(readOnly = true)
    public SystemSummaryDTO getSystemSummary() {
        SystemSummaryDTO summary = new SystemSummaryDTO();

        // นับจำนวน Stock Lots
        long totalLots = stockLotRepository.count();
        summary.setTotalLots((int) totalLots);

        // ดึง Stock Lots ทั้งหมด
        List<StockLot> allLots = stockLotRepository.findAll();

        int totalItems = 0;
        int totalChinaItems = 0;
        int totalThaiItems = 0;
        int activeItems = 0;
        BigDecimal totalInventoryValue = BigDecimal.ZERO;

        for (StockLot lot : allLots) {
            List<StockBase> items = lot.getItems();
            if (items != null && !items.isEmpty()) {
                totalItems += items.size();

                for (StockBase item : items) {
                    // นับประเภท
                    if (item instanceof ChinaStock) {
                        totalChinaItems++;
                        ChinaStock chinaStock = (ChinaStock) item;

                        // ⭐ ใช้ calculateTotalCost() ที่รวม Buffer
                        totalInventoryValue = totalInventoryValue.add(chinaStock.calculateTotalCost());

                        if (StockBase.StockStatus.ACTIVE.equals(chinaStock.getStatus())) {
                            activeItems++;
                        }
                    } else if (item instanceof ThaiStock) {
                        totalThaiItems++;
                        ThaiStock thaiStock = (ThaiStock) item;

                        // ⭐ ใช้ calculateTotalCost() ที่รวม Buffer
                        totalInventoryValue = totalInventoryValue.add(thaiStock.calculateTotalCost());

                        if (StockBase.StockStatus.ACTIVE.equals(thaiStock.getStatus())) {
                            activeItems++;
                        }
                    }
                }
            }
        }

        summary.setTotalItems(totalItems);
        summary.setTotalChinaItems(totalChinaItems);
        summary.setTotalThaiItems(totalThaiItems);
        summary.setActiveItems(activeItems);
        summary.setTotalInventoryValue(totalInventoryValue);

        return summary;
    }

}