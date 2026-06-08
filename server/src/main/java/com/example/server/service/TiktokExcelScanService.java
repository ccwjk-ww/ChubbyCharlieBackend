package com.example.server.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ⭐ TikTok Excel Scan Service — อ่าน XML โดยตรง
 *
 * ปัญหา Apache POI: ไฟล์ TikTok Excel นี้เก็บแต่ละ cell ไว้ใน <row> element แยกกัน
 * (63 <row> elements ต่อ 1 แถวข้อมูล) ซึ่ง POI ไม่ aggregate ได้ถูกต้อง → rows = []
 *
 * วิธีแก้: อ่าน xl/worksheets/sheet2.xml โดยตรง แล้ว aggregate cells ด้วย row number
 * จาก cell reference (เช่น "A3" → row=3, col=A=0)
 *
 * Column mapping (0-based, confirmed):
 *   A=0   Order ID
 *   B=1   Order Status
 *   F=5   SKU ID
 *   G=6   Seller SKU
 *   H=7   Product Name
 *   I=8   Variation
 *   J=9   Quantity
 *   L=11  SKU Unit Original Price
 *   M=12  SKU Subtotal Before Discount
 *   O=14  SKU Seller Discount
 *   Q=16  Shipping Fee After Discount
 *   W=22  Order Amount
 *   Y=24  Created Time
 */
@Service
public class TiktokExcelScanService {

    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.07");

    private static final int COL_ORDER_ID      = 0;
    private static final int COL_ORDER_STATUS  = 1;
    private static final int COL_SKU_ID        = 5;
    private static final int COL_SELLER_SKU    = 6;
    private static final int COL_PRODUCT_NAME  = 7;
    private static final int COL_QUANTITY      = 9;
    private static final int COL_UNIT_PRICE    = 11;
    private static final int COL_SUBTOTAL      = 12;
    private static final int COL_SELLER_DISC   = 14;
    private static final int COL_SHIPPING      = 16;
    private static final int COL_ORDER_AMOUNT  = 22;
    private static final int COL_CREATED_TIME  = 24;

    public Map<String, Object> scanTiktokExcel(MultipartFile file) throws IOException {
        // อ่าน XML จาก xlsx (zip) โดยตรง
        Map<Integer, Map<Integer, String>> rowData = parseXlsxDirect(file.getInputStream());
        return processRowData(rowData);
    }

    // ── Direct XML parser ────────────────────────────────────────────────────────

    /**
     * อ่าน xl/worksheets/sheet[1|2].xml จาก .xlsx (zip)
     * แล้ว aggregate cells โดยใช้ row number จาก cell reference
     * เช่น "A3" → row=3, colIdx=0
     */
    private Map<Integer, Map<Integer, String>> parseXlsxDirect(InputStream fileStream) throws IOException {
        // row_number (1-based) → { colIdx → value }
        Map<Integer, Map<Integer, String>> result = new TreeMap<>();

        try (ZipInputStream zip = new ZipInputStream(fileStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                // TikTok uses sheet2.xml; fallback to sheet1.xml
                if (name.equals("xl/worksheets/sheet2.xml") || name.equals("xl/worksheets/sheet1.xml")) {
                    // Read this entry fully into a byte array
                    byte[] bytes = zip.readAllBytes();
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(false);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(new java.io.ByteArrayInputStream(bytes));

                    NodeList cNodes = doc.getElementsByTagName("c");
                    for (int i = 0; i < cNodes.getLength(); i++) {
                        Element c = (Element) cNodes.item(i);
                        String ref = c.getAttribute("r"); // e.g. "A3"
                        if (ref == null || ref.isEmpty()) continue;

                        String colStr = ref.replaceAll("[0-9]", "");
                        String rowStr = ref.replaceAll("[^0-9]", "");
                        if (colStr.isEmpty() || rowStr.isEmpty()) continue;

                        int rowNum = Integer.parseInt(rowStr);
                        int colIdx = colLetterToIndex(colStr);

                        // Get cell value from <v> child
                        NodeList vNodes = c.getElementsByTagName("v");
                        String val = (vNodes.getLength() > 0) ? vNodes.item(0).getTextContent() : "";

                        result.computeIfAbsent(rowNum, k -> new TreeMap<>()).put(colIdx, val);
                    }

                    // Prefer sheet2.xml; stop if found
                    if (name.equals("xl/worksheets/sheet2.xml")) break;
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse TikTok Excel XML: " + e.getMessage(), e);
        }

        return result;
    }

    // ── Process ──────────────────────────────────────────────────────────────────

    private Map<String, Object> processRowData(Map<Integer, Map<Integer, String>> allRows) {
        List<Map<String, Object>> rows = new ArrayList<>();

        BigDecimal totalSubtotal   = BigDecimal.ZERO;
        BigDecimal totalDiscount   = BigDecimal.ZERO;
        BigDecimal totalShipping   = BigDecimal.ZERO;
        BigDecimal totalSalesVat   = BigDecimal.ZERO;
        BigDecimal totalSalesExVat = BigDecimal.ZERO;
        BigDecimal totalVat        = BigDecimal.ZERO;

        // Rows 1=header, 2=description, 3+=data
        for (Map.Entry<Integer, Map<Integer, String>> entry : allRows.entrySet()) {
            int rowNum = entry.getKey();
            if (rowNum <= 2) continue; // skip header & description

            Map<Integer, String> cols = entry.getValue();

            String orderId = cols.getOrDefault(COL_ORDER_ID, "").trim();
            if (orderId.isEmpty()) continue;
            if (isHeaderRow(orderId)) continue;

            String orderStatus = cols.getOrDefault(COL_ORDER_STATUS, "");
            String skuId       = cols.getOrDefault(COL_SKU_ID, "");
            String sellerSku   = cols.getOrDefault(COL_SELLER_SKU, "");
            String productName = cols.getOrDefault(COL_PRODUCT_NAME, "");
            int    quantity    = parseInt(cols.getOrDefault(COL_QUANTITY, "0"));
            String createdTime = cols.getOrDefault(COL_CREATED_TIME, "");

            BigDecimal unitPrice = parseDecimal(cols.getOrDefault(COL_UNIT_PRICE, "0"));
            BigDecimal subtotal  = parseDecimal(cols.getOrDefault(COL_SUBTOTAL, "0"));
            BigDecimal discount  = parseDecimal(cols.getOrDefault(COL_SELLER_DISC, "0"));
            BigDecimal shipping  = parseDecimal(cols.getOrDefault(COL_SHIPPING, "0"));

            // VAT calculation: salesWithVat = subtotal - discount + shipping
            BigDecimal salesWithVat = subtotal.subtract(discount).add(shipping)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal salesExVat = salesWithVat.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
            BigDecimal vat        = salesWithVat.subtract(salesExVat).setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("orderId",                   orderId);
            rowData.put("orderStatus",               orderStatus);
            rowData.put("skuId",                     skuId);
            rowData.put("sellerSku",                 sellerSku);
            rowData.put("productName",               productName);
            rowData.put("quantity",                  quantity);
            rowData.put("skuUnitOriginalPrice",      unitPrice);
            rowData.put("skuSubtotalBeforeDiscount", subtotal);
            rowData.put("skuSellerDiscount",         discount);
            rowData.put("shippingFeeAfterDiscount",  shipping);
            rowData.put("salesWithVat",              salesWithVat);
            rowData.put("salesExVat",                salesExVat);
            rowData.put("vat",                       vat);
            rowData.put("createdTime",               createdTime);

            rows.add(rowData);
            totalSubtotal   = totalSubtotal.add(subtotal);
            totalDiscount   = totalDiscount.add(discount);
            totalShipping   = totalShipping.add(shipping);
            totalSalesVat   = totalSalesVat.add(salesWithVat);
            totalSalesExVat = totalSalesExVat.add(salesExVat);
            totalVat        = totalVat.add(vat);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRows",         rows.size());
        summary.put("totalSubtotal",     totalSubtotal.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalDiscount",     totalDiscount.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalShipping",     totalShipping.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalSalesWithVat", totalSalesVat.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalSalesExVat",   totalSalesExVat.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalVat",          totalVat.setScale(2, RoundingMode.HALF_UP));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success",    true);
        result.put("rows",       rows);
        result.put("summary",    summary);
        result.put("parsedWith", "Direct XML Parser (TikTok non-standard format)");

        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Convert column letters to 0-based index: A=0, B=1, ..., Z=25, AA=26 */
    private static int colLetterToIndex(String colStr) {
        int result = 0;
        for (char c : colStr.toUpperCase().toCharArray()) {
            result = result * 26 + (c - 'A' + 1);
        }
        return result - 1;
    }

    private boolean isHeaderRow(String orderId) {
        String lower = orderId.toLowerCase();
        return lower.contains("order") || lower.contains("platform");
    }

    private int parseInt(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try {
            String clean = s.trim().replaceAll("[^0-9]", "");
            return clean.isEmpty() ? 0 : Integer.parseInt(clean);
        } catch (Exception e) { return 0; }
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            String clean = s.trim().replaceAll("[^0-9.]", "");
            if (clean.isEmpty()) return BigDecimal.ZERO;
            return new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }
}