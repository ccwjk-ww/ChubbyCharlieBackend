package com.example.server.service;

import com.example.server.entity.Customer;
import com.example.server.entity.Order;
import com.example.server.entity.OrderItem;
import com.example.server.respository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ⭐ TikTok Excel Import Service — อ่าน XML โดยตรง (ไม่ใช้ Apache POI)
 *
 * ปัญหา POI: ไฟล์ TikTok เก็บแต่ละ cell ใน <row> element แยกกัน
 * → POI ไม่ aggregate → getLastRowNum()=58 แต่ getRow(r) ได้ cell เดียว → rows=[]
 *
 * วิธีแก้: อ่าน XML ด้วย javax.xml.parsers แล้ว group cells ตาม row number
 */
@Service
public class TiktokExcelImportService {

    private static final BigDecimal VAT_DIVISOR = new BigDecimal("1.07");
    private static final BigDecimal VAT_RATE    = new BigDecimal("7");

    private static final int COL_ORDER_ID      = 0;
    private static final int COL_ORDER_STATUS  = 1;
    private static final int COL_SKU_ID        = 5;
    private static final int COL_SELLER_SKU    = 6;
    private static final int COL_PRODUCT_NAME  = 7;
    private static final int COL_VARIATION     = 8;
    private static final int COL_QUANTITY      = 9;
    private static final int COL_UNIT_PRICE    = 11;
    private static final int COL_SUBTOTAL      = 12;
    private static final int COL_PLATFORM_DISC = 13;
    private static final int COL_SELLER_DISC   = 14;
    private static final int COL_SHIPPING      = 16;
    private static final int COL_ORDER_AMOUNT  = 22;
    private static final int COL_CREATED_TIME  = 24;

    @Autowired
    private CustomerRepository customerRepository;

    // ── Public API ───────────────────────────────────────────────────────────────

    public List<Order> importOrders(MultipartFile file, Long customerId) throws IOException {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("ไม่พบลูกค้า ID: " + customerId));

        Map<Integer, Map<Integer, String>> allRows = parseXlsxDirect(file.getInputStream());
        List<Order> orders = new ArrayList<>();

        for (Map.Entry<Integer, Map<Integer, String>> entry : allRows.entrySet()) {
            if (entry.getKey() <= 2) continue;
            Map<Integer, String> cols = entry.getValue();

            String orderId = cols.getOrDefault(COL_ORDER_ID, "").trim();
            if (orderId.isEmpty() || isHeaderRow(orderId)) continue;

            String orderStatus = cols.getOrDefault(COL_ORDER_STATUS, "");
            orders.add(buildOrder(cols, orderId, orderStatus, customer, file.getOriginalFilename()));
        }

        return orders;
    }

    public Map<String, Object> previewOrders(MultipartFile file, Long customerId) throws IOException {
        Customer customer = null;
        if (customerId != null) {
            customer = customerRepository.findById(customerId).orElse(null);
        }

        Map<Integer, Map<Integer, String>> allRows = parseXlsxDirect(file.getInputStream());
        List<Map<String, Object>> orderPreviews = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Map.Entry<Integer, Map<Integer, String>> entry : allRows.entrySet()) {
            if (entry.getKey() <= 2) continue;
            Map<Integer, String> cols = entry.getValue();

            String orderId = cols.getOrDefault(COL_ORDER_ID, "").trim();
            if (orderId.isEmpty() || isHeaderRow(orderId)) continue;

            String orderStatus = cols.getOrDefault(COL_ORDER_STATUS, "");
            String skuId       = cols.getOrDefault(COL_SKU_ID, "");
            String sellerSku   = cols.getOrDefault(COL_SELLER_SKU, "");
            String productName = cols.getOrDefault(COL_PRODUCT_NAME, "");
            String variation   = cols.getOrDefault(COL_VARIATION, "");
            int    quantity    = parseInt(cols.getOrDefault(COL_QUANTITY, "0"));
            String createdTime = cols.getOrDefault(COL_CREATED_TIME, "");

            BigDecimal unitPrice    = parseDecimal(cols.getOrDefault(COL_UNIT_PRICE, "0"));
            BigDecimal subtotal     = parseDecimal(cols.getOrDefault(COL_SUBTOTAL, "0"));
            BigDecimal sellerDisc   = parseDecimal(cols.getOrDefault(COL_SELLER_DISC, "0"));
            BigDecimal platformDisc = parseDecimal(cols.getOrDefault(COL_PLATFORM_DISC, "0"));
            BigDecimal shipping     = parseDecimal(cols.getOrDefault(COL_SHIPPING, "0"));
            BigDecimal orderAmount  = parseDecimal(cols.getOrDefault(COL_ORDER_AMOUNT, "0"));

            BigDecimal salesExVat = orderAmount.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
            BigDecimal vat        = orderAmount.subtract(salesExVat).setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("orderId",          orderId);
            preview.put("orderStatus",      orderStatus);
            preview.put("skuId",            skuId);
            preview.put("sellerSku",        sellerSku);
            preview.put("productName",      productName);
            preview.put("variation",        variation);
            preview.put("quantity",         quantity);
            preview.put("unitPrice",        unitPrice);
            preview.put("subtotal",         subtotal);
            preview.put("sellerDiscount",   sellerDisc);
            preview.put("platformDiscount", platformDisc);
            preview.put("shipping",         shipping);
            preview.put("orderAmount",      orderAmount);
            preview.put("salesWithVat",     orderAmount);
            preview.put("salesExVat",       salesExVat);
            preview.put("vat",              vat);
            preview.put("createdTime",      createdTime);
            preview.put("customerName",     customer != null ? customer.getCustomerName() : "TikTok Customer");

            orderPreviews.add(preview);
            grandTotal = grandTotal.add(orderAmount);
        }

        BigDecimal totalExVat = grandTotal.divide(VAT_DIVISOR, 2, RoundingMode.HALF_UP);
        BigDecimal totalVat   = grandTotal.subtract(totalExVat).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOrders", orderPreviews.size());
        summary.put("totalAmount", grandTotal.setScale(2, RoundingMode.HALF_UP));
        summary.put("totalExVat",  totalExVat);
        summary.put("totalVat",    totalVat);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success",     true);
        result.put("totalOrders", orderPreviews.size());
        result.put("totalItems",  orderPreviews.size());
        result.put("orders",      orderPreviews);
        result.put("summary",     summary);
        result.put("parsedWith",  "Direct XML Parser (TikTok non-standard format)");

        return result;
    }

    // ── Direct XML Parser ────────────────────────────────────────────────────────

    private Map<Integer, Map<Integer, String>> parseXlsxDirect(InputStream fileStream) throws IOException {
        Map<Integer, Map<Integer, String>> result = new TreeMap<>();

        try (ZipInputStream zip = new ZipInputStream(fileStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("xl/worksheets/sheet2.xml") || name.equals("xl/worksheets/sheet1.xml")) {
                    byte[] bytes = zip.readAllBytes();

                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    dbf.setNamespaceAware(false);
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(new java.io.ByteArrayInputStream(bytes));

                    NodeList cNodes = doc.getElementsByTagName("c");
                    for (int i = 0; i < cNodes.getLength(); i++) {
                        Element c = (Element) cNodes.item(i);
                        String ref = c.getAttribute("r");
                        if (ref == null || ref.isEmpty()) continue;

                        String colStr = ref.replaceAll("[0-9]", "");
                        String rowStr = ref.replaceAll("[^0-9]", "");
                        if (colStr.isEmpty() || rowStr.isEmpty()) continue;

                        int rowNum = Integer.parseInt(rowStr);
                        int colIdx = colLetterToIndex(colStr);

                        NodeList vNodes = c.getElementsByTagName("v");
                        String val = (vNodes.getLength() > 0) ? vNodes.item(0).getTextContent().trim() : "";

                        result.computeIfAbsent(rowNum, k -> new TreeMap<>()).put(colIdx, val);
                    }

                    if (name.equals("xl/worksheets/sheet2.xml")) break;
                }
                zip.closeEntry();
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse TikTok Excel: " + e.getMessage(), e);
        }

        return result;
    }

    // ── Build Order ──────────────────────────────────────────────────────────────

    private Order buildOrder(Map<Integer, String> cols, String tiktokOrderId,
                             String orderStatus, Customer customer, String fileName) {
        String productName  = cols.getOrDefault(COL_PRODUCT_NAME, "");
        String variation    = cols.getOrDefault(COL_VARIATION, "");
        String skuId        = cols.getOrDefault(COL_SKU_ID, "");
        String sellerSku    = cols.getOrDefault(COL_SELLER_SKU, "");
        int    quantity     = parseInt(cols.getOrDefault(COL_QUANTITY, "1"));
        String createdTime  = cols.getOrDefault(COL_CREATED_TIME, "");

        BigDecimal unitPrice    = parseDecimal(cols.getOrDefault(COL_UNIT_PRICE, "0"));
        BigDecimal subtotal     = parseDecimal(cols.getOrDefault(COL_SUBTOTAL, "0"));
        BigDecimal sellerDisc   = parseDecimal(cols.getOrDefault(COL_SELLER_DISC, "0"));
        BigDecimal platformDisc = parseDecimal(cols.getOrDefault(COL_PLATFORM_DISC, "0"));
        BigDecimal shipping     = parseDecimal(cols.getOrDefault(COL_SHIPPING, "0"));
        BigDecimal orderAmount  = parseDecimal(cols.getOrDefault(COL_ORDER_AMOUNT, "0"));

        Order order = new Order();
        order.setOrderNumber(tiktokOrderId);
        order.setSource(Order.OrderSource.TIKTOK);
        order.setCustomer(customer);
        order.setCustomerName(customer.getCustomerName());
        order.setCustomerPhone(customer.getCustomerPhone());
        order.setShippingAddress(customer.getCustomerAddress());
        order.setOrderDate(parseDateTime(createdTime));
        order.setStatus(mapOrderStatus(orderStatus));
        order.setPaymentStatus(Order.PaymentStatus.UNPAID);
        order.setShippingFee(shipping);
        order.setDiscount(sellerDisc.add(platformDisc));
        order.setNotes("TikTok Order: " + tiktokOrderId
                + (variation != null && !variation.isEmpty() ? " | " + variation : ""));
        order.setOriginalFileName(fileName);
        order.setVatEnabled(true);
        order.setVatRate(VAT_RATE);
        BigDecimal vatAmt = orderAmount
                .subtract(orderAmount.divide(VAT_DIVISOR, 10, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
        order.setVatAmount(vatAmt);
        order.setTotalAmount(subtotal);
        order.setNetAmount(orderAmount);

        OrderItem item = new OrderItem();
        String displayName = productName;
        if (variation != null && !variation.isEmpty()) {
            displayName = productName + " (" + variation + ")";
        }
        item.setProductName(displayName.isEmpty() ? "TikTok Product" : displayName);
        String sku = (sellerSku != null && !sellerSku.trim().isEmpty()) ? sellerSku : skuId;
        item.setProductSku(sku);
        item.setQuantity(Math.max(1, quantity));
        item.setUnitPrice(unitPrice);
        item.setDiscount(sellerDisc);
        item.setTotalPrice(orderAmount);
        item.setNotes("SKU ID: " + skuId + (variation != null && !variation.isEmpty() ? " | " + variation : ""));
        item.setStockDeductionStatus(OrderItem.StockDeductionStatus.PENDING);

        order.getOrderItems().add(item);
        return order;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

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

    private Order.OrderStatus mapOrderStatus(String status) {
        if (status == null) return Order.OrderStatus.DELIVERED;
        switch (status.trim()) {
            case "เสร็จสมบูรณ์": case "Completed": case "Delivered": return Order.OrderStatus.DELIVERED;
            case "จัดส่งแล้ว": case "กำลังจัดส่ง": case "In Transit": case "Shipped": return Order.OrderStatus.SHIPPED;
            case "ยกเลิกแล้ว": case "Cancelled": return Order.OrderStatus.CANCELLED;
            case "รอดำเนินการ": case "Pending": return Order.OrderStatus.PENDING;
            default: return Order.OrderStatus.DELIVERED;
        }
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return LocalDateTime.now();
        String[] patterns = {"dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "dd/MM/yyyy", "yyyy-MM-dd"};
        for (String p : patterns) {
            try {
                Date d = new SimpleDateFormat(p).parse(dateStr.trim());
                return d.toInstant().atZone(ZoneId.of("Asia/Bangkok")).toLocalDateTime();
            } catch (ParseException ignored) {}
        }
        return LocalDateTime.now();
    }

    private int parseInt(String s) {
        if (s == null || s.trim().isEmpty()) return 0;
        try { return Integer.parseInt(s.trim().replaceAll("[^0-9]", "")); }
        catch (Exception e) { return 0; }
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try {
            String clean = s.trim().replaceAll("[^0-9.]", "");
            return clean.isEmpty() ? BigDecimal.ZERO : new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) { return BigDecimal.ZERO; }
    }
}