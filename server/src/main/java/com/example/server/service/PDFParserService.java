package com.example.server.service;

import com.example.server.entity.OrderItem;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PDFParserService {

    /**
     * Parse รายการสินค้าจาก PDF ของ 24Shopping
     */
    public List<OrderItem> parseOrderItemsFromPDF(MultipartFile file) throws IOException {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            System.out.println("========== PDF Content ==========");
            System.out.println(text);
            System.out.println("=================================");

            return parseOrderItems(text);
        }
    }

    private List<OrderItem> parseOrderItems(String text) {
        List<OrderItem> items = new ArrayList<>();

        // Pattern ที่ปรับปรุงแล้ว - รองรับข้อมูลจาก PDF จริง
        // Format: No. | SKU (6 digits) | SKU (8 digits) | ชื่อสินค้า | EXC | ... | ปริมาณ | ... | ราคาต่อหน่วย | ... | จำนวนเงิน
        Pattern itemPattern = Pattern.compile(
                "(\\d+)\\s+" +                          // 1. No.
                        "(\\d{6})\\s+" +                        // 2. SKU 6 หลัก
                        "\\d{8}\\s+" +                          // SKU 8 หลัก (ข้าม)
                        "(.+?)\\s+" +                           // 3. ชื่อสินค้า
                        "EXC\\s+[\\d.]+\\s+" +                 // %VAT
                        "[\\d.]+\\s+" +                         // ขนาดบรรจุ (ปริมาณ/หีบ)
                        "([\\d,]+\\.\\d{2})\\s+" +             // 4. ปริมาณ (จำนวนหีบ/หน่วย)
                        "[\\d.]+\\s+" +                         // ปริมาณแถม
                        "([\\d,]+\\.\\d{2})\\s+" +             // 5. ราคาต่อหน่วย
                        "[\\d.\\s]+\\s+" +                      // ส่วนลด
                        "([\\d,]+\\.\\d{2})"                   // 6. จำนวนเงิน
        );

        Matcher matcher = itemPattern.matcher(text);
        int foundCount = 0;

        while (matcher.find()) {
            try {
                OrderItem item = new OrderItem();
                foundCount++;

                // SKU 6 หลัก
                String sku = matcher.group(2).trim();
                item.setProductSku(sku);

                // ชื่อสินค้า
                String productName = matcher.group(3).trim();
                item.setProductName(productName);

                // ปริมาณ (จำนวนหีบ/หน่วย) - ตัวที่ 4
                String quantityStr = matcher.group(4).replace(",", "");
                double quantityDouble = Double.parseDouble(quantityStr);
                int quantity = (int) quantityDouble;
                item.setQuantity(quantity);

                // ราคาต่อหน่วย
                String unitPriceStr = matcher.group(5).replace(",", "");
                BigDecimal unitPrice = new BigDecimal(unitPriceStr);
                item.setUnitPrice(unitPrice);

                // จำนวนเงินรวม
                String totalPriceStr = matcher.group(6).replace(",", "");
                BigDecimal totalPrice = new BigDecimal(totalPriceStr);
                item.setTotalPrice(totalPrice);

                // กำหนดสถานะ Stock
                item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);
                item.setDiscount(BigDecimal.ZERO);

                items.add(item);

                System.out.println("✓ Parsed item #" + foundCount + ": " +
                        "SKU=" + sku +
                        ", Name=" + productName +
                        ", Qty=" + quantity +
                        ", UnitPrice=" + unitPrice +
                        ", Total=" + totalPrice);

            } catch (Exception e) {
                System.err.println("❌ Error parsing item: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ถ้าไม่เจอด้วย pattern หลัก ลอง pattern สำรอง
        if (items.isEmpty()) {
            System.out.println("⚠️ Primary pattern failed, trying alternative...");
            items = parseOrderItemsAlternative(text);
        }

        System.out.println("========== Parse Summary ==========");
        System.out.println("Total items found: " + items.size());
        System.out.println("===================================");

        return items;
    }

    /**
     * Pattern สำรอง - พยายามจับข้อมูลพื้นฐาน
     */
    private List<OrderItem> parseOrderItemsAlternative(String text) {
        List<OrderItem> items = new ArrayList<>();

        // ลองหาแบบง่ายขึ้น - เฉพาะ SKU และชื่อสินค้า
        Pattern simplePattern = Pattern.compile(
                "\\d+\\s+" +                    // No.
                        "(\\d{6})\\s+" +                // SKU 6 หลัก
                        "\\d{8}\\s+" +                  // SKU 8 หลัก
                        "(.+?)\\s+EXC",                 // ชื่อสินค้า (จนถึง EXC)
                Pattern.MULTILINE
        );

        Matcher matcher = simplePattern.matcher(text);

        while (matcher.find()) {
            try {
                OrderItem item = new OrderItem();
                item.setProductSku(matcher.group(1).trim());
                item.setProductName(matcher.group(2).trim());
                item.setQuantity(1); // default
                item.setUnitPrice(BigDecimal.ZERO);
                item.setTotalPrice(BigDecimal.ZERO);
                item.setDiscount(BigDecimal.ZERO);
                item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);

                items.add(item);
                System.out.println("✓ Alternative parse: " + item.getProductSku() + " - " + item.getProductName());

            } catch (Exception e) {
                System.err.println("❌ Alternative parse error: " + e.getMessage());
            }
        }

        return items;
    }
}