package com.example.server.service;

import com.example.server.entity.Order;
import com.example.server.entity.OrderItem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class ExcelParserService {

    public List<Order> parseShopeeExcel(MultipartFile file) throws IOException {
        List<Order> orders = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);

            int startRow = findHeaderRow(sheet);
            if (startRow == -1) {
                throw new IllegalArgumentException("Could not find header row");
            }

            Row headerRow = sheet.getRow(startRow);
            ExcelColumnIndices indices = findColumnIndices(headerRow);

            for (int i = startRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                try {
                    Order order = parseOrderFromRow(row, indices);
                    if (order != null) {
                        orders.add(order);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing row " + i + ": " + e.getMessage());
                }
            }
        }

        return orders;
    }

    private int findHeaderRow(Sheet sheet) {
        for (int i = 0; i < Math.min(10, sheet.getLastRowNum()); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            for (Cell cell : row) {
                String value = getCellValueAsString(cell).toLowerCase();
                if (value.contains("order") || value.contains("คำสั่งซื้อ")) {
                    return i;
                }
            }
        }
        return 0;
    }

    private ExcelColumnIndices findColumnIndices(Row headerRow) {
        ExcelColumnIndices indices = new ExcelColumnIndices();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) continue;

            String header = getCellValueAsString(cell).toLowerCase().trim();

            if (header.contains("order") && (header.contains("number") || header.contains("no"))) {
                indices.orderNumber = i;
            } else if (header.contains("buyer") || header.contains("customer") ||
                    (header.contains("ชื่อ") && header.contains("ผู้ซื้อ"))) {
                indices.customerName = i;
            } else if (header.contains("phone") || header.contains("เบอร์")) {
                indices.phone = i;
            } else if (header.contains("address") || header.contains("ที่อยู่")) {
                indices.address = i;
            } else if (header.contains("product") && header.contains("name") ||
                    (header.contains("ชื่อ") && header.contains("สินค้า"))) {
                indices.productName = i;
            } else if (header.contains("sku") || header.contains("รหัส")) {
                indices.sku = i;
            } else if (header.contains("quantity") || header.contains("จำนวน")) {
                indices.quantity = i;
            } else if ((header.contains("price") || header.contains("ราคา")) && !header.contains("total")) {
                indices.unitPrice = i;
            } else if (header.contains("total") || header.contains("รวม")) {
                indices.total = i;
            } else if (header.contains("order") && header.contains("date") || header.contains("วันที่")) {
                indices.orderDate = i;
            }
        }

        return indices;
    }

    private Order parseOrderFromRow(Row row, ExcelColumnIndices indices) {
        String orderNumber = getCellValueAsString(row.getCell(indices.orderNumber));
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            return null;
        }

        Order order = new Order();
        order.setSource(Order.OrderSource.SHOPEE);
        order.setOrderNumber(orderNumber);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setPaymentStatus(Order.PaymentStatus.UNPAID);

        if (indices.customerName >= 0) {
            order.setCustomerName(getCellValueAsString(row.getCell(indices.customerName)));
        }
        if (indices.phone >= 0) {
            order.setCustomerPhone(getCellValueAsString(row.getCell(indices.phone)));
        }
        if (indices.address >= 0) {
            order.setShippingAddress(getCellValueAsString(row.getCell(indices.address)));
        }

        if (indices.orderDate >= 0) {
            Cell dateCell = row.getCell(indices.orderDate);
            if (dateCell != null) {
                if (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell)) {
                    Date date = dateCell.getDateCellValue();
                    order.setOrderDate(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                } else {
                    order.setOrderDate(LocalDateTime.now());
                }
            }
        }

        OrderItem item = new OrderItem();
        item.setOrder(order);

        if (indices.productName >= 0) {
            item.setProductName(getCellValueAsString(row.getCell(indices.productName)));
        }
        if (indices.sku >= 0) {
            item.setProductSku(getCellValueAsString(row.getCell(indices.sku)));
        }
        if (indices.quantity >= 0) {
            item.setQuantity((int) getCellValueAsNumber(row.getCell(indices.quantity)));
        }
        if (indices.unitPrice >= 0) {
            item.setUnitPrice(BigDecimal.valueOf(getCellValueAsNumber(row.getCell(indices.unitPrice))));
        }
        if (indices.total >= 0) {
            item.setTotalPrice(BigDecimal.valueOf(getCellValueAsNumber(row.getCell(indices.total))));
        }

        item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);

        List<OrderItem> items = new ArrayList<>();
        items.add(item);
        order.setOrderItems(items);
        order.calculateTotals();

        return order;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private double getCellValueAsNumber(Cell cell) {
        if (cell == null) return 0.0;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return cell.getNumericCellValue();
                case STRING:
                    String value = cell.getStringCellValue().replaceAll("[^0-9.]", "");
                    return value.isEmpty() ? 0.0 : Double.parseDouble(value);
                default:
                    return 0.0;
            }
        } catch (Exception e) {
            return 0.0;
        }
    }

    private boolean isEmptyRow(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static class ExcelColumnIndices {
        int orderNumber = -1;
        int customerName = -1;
        int phone = -1;
        int address = -1;
        int productName = -1;
        int sku = -1;
        int quantity = -1;
        int unitPrice = -1;
        int total = -1;
        int orderDate = -1;
    }
}