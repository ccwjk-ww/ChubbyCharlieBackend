package com.example.server.service;

import com.example.server.dto.ChatRequest;
import com.example.server.dto.ChatResponse;
import com.example.server.entity.Product;
import com.example.server.entity.StockLot;
import com.example.server.entity.StockForecast;
import com.example.server.respository.ProductRepository;
import com.example.server.respository.StockLotRepository;
import com.example.server.respository.StockForecastRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SmartChatService {

    private final GeminiService geminiService;
    private final ProductRepository productRepository;
    private final StockLotRepository stockLotRepository;
    private final StockForecastRepository stockForecastRepository;

    /**
     * Chat แบบอัจฉริยะ - ดึงข้อมูลจาก database มาช่วยตอบ
     */
    public Mono<ChatResponse> smartChat(String message) {
        // ตรวจสอบว่าคำถามเกี่ยวกับอะไร
        if (containsKeywords(message, "สินค้า", "product", "มีอะไรบ้าง", "ราคา")) {
            return chatWithProductContext(message);
        } else if (containsKeywords(message, "สต็อก", "stock", "คงเหลือ", "lot")) {
            return chatWithStockContext(message);
        } else if (containsKeywords(message, "หมด", "ใกล้หมด", "low stock", "เร่งด่วน", "urgent")) {
            return chatWithLowStockContext(message);
        } else if (containsKeywords(message, "ยอดขาย", "sales", "รายงาน", "report")) {
            return chatWithSalesContext(message);
        } else {
            // คำถามทั่วไป
            return geminiService.chat(new ChatRequest(message, null));
        }
    }

    /**
     * Chat พร้อมข้อมูลสินค้า
     */
    private Mono<ChatResponse> chatWithProductContext(String message) {
        // ดึงข้อมูลสินค้าจาก database
        List<Product> products = productRepository.findAll();

        // สร้าง context
        StringBuilder context = new StringBuilder();
        context.append("สินค้าในระบบทั้งหมด ").append(products.size()).append(" รายการ:\n\n");

        products.forEach(product -> {
            context.append(String.format(
                    "📦 %s\n" +
                            "   - รหัส: %s\n" +
                            "   - หมวดหมู่: %s\n" +
                            "   - ราคาขาย: %.2f บาท\n" +
                            "   - ราคาทุน: %.2f บาท\n" +
                            "   - สถานะ: %s\n\n",
                    product.getProductName(),
                    product.getSku(),
                    product.getCategory() != null ? product.getCategory() : "ไม่ระบุ",
                    product.getSellingPrice(),
                    product.getCalculatedCost(),
                    product.getStatus()
            ));
        });

        // ส่งไปยัง Gemini
        return geminiService.chat(new ChatRequest(message, context.toString()));
    }

    /**
     * Chat พร้อมข้อมูลสต็อก
     */
    private Mono<ChatResponse> chatWithStockContext(String message) {
        // ดึงข้อมูลสต็อก Lot
        List<StockLot> stockLots = stockLotRepository.findAll();

        StringBuilder context = new StringBuilder();
        context.append("📊 สถานะสต็อกปัจจุบัน (").append(stockLots.size()).append(" Lot):\n\n");

        stockLots.forEach(lot -> {
            context.append(String.format(
                    "📦 Lot: %s\n" +
                            "   - สถานะ: %s\n" +
                            "   - วันที่นำเข้า: %s\n" +
                            "   - วันที่ถึงไทย: %s\n\n",
                    lot.getLotName(),
                    lot.getStatus(),
                    lot.getImportDate() != null ? lot.getImportDate().toString() : "ยังไม่นำเข้า",
                    lot.getArrivalDate() != null ? lot.getArrivalDate().toString() : "ยังไม่ถึง"
            ));
        });

        return geminiService.chat(new ChatRequest(message, context.toString()));
    }

    /**
     * Chat พร้อมข้อมูลสต็อกใกล้หมด (ใช้ StockForecast)
     */
    private Mono<ChatResponse> chatWithLowStockContext(String message) {
        // ดึงสินค้าที่ใกล้หมดจาก StockForecast
        List<StockForecast> urgentStocks = stockForecastRepository.findUrgentStockItems();

        StringBuilder context = new StringBuilder();
        context.append("⚠️ รายการสต็อกที่ต้องเร่งด่วน:\n\n");

        if (urgentStocks.isEmpty()) {
            context.append("✅ ไม่มีสต็อกที่ใกล้หมดในขณะนี้\n");
        } else {
            urgentStocks.forEach(forecast -> {
                context.append(String.format(
                        "🚨 %s\n" +
                                "   - ระดับความเร่งด่วน: %s\n" +
                                "   - จะหมดใน: %d วัน\n" +
                                "   - ใช้เฉลี่ย: %.2f ต่อวัน\n" +
                                "   - ควรสั่ง: %.0f หน่วย\n" +
                                "   - ประมาณการค่าใช้จ่าย: %.2f บาท\n\n",
                        forecast.getStockType(),
                        forecast.getUrgencyLevel(),
                        forecast.getDaysUntilStockOut(),
                        forecast.getAverageDailyUsage(),
                        forecast.getRecommendedOrderQuantity(),
                        forecast.getEstimatedOrderCost()
                ));
            });
        }

        return geminiService.chat(new ChatRequest(message, context.toString()));
    }

    /**
     * Chat พร้อมข้อมูลยอดขาย
     */
    private Mono<ChatResponse> chatWithSalesContext(String message) {
        // ดึงข้อมูลสินค้า
        List<Product> products = productRepository.findAll();

        StringBuilder context = new StringBuilder();
        context.append("💰 ข้อมูลสินค้าและราคา:\n\n");

        // คำนวณสรุป
        double totalSellingPrice = products.stream()
                .mapToDouble(p -> p.getSellingPrice().doubleValue())
                .sum();

        double totalCost = products.stream()
                .mapToDouble(p -> p.getCalculatedCost().doubleValue())
                .sum();

        context.append(String.format(
                "📈 สรุปภาพรวม:\n" +
                        "   - จำนวนสินค้าทั้งหมด: %d รายการ\n" +
                        "   - มูลค่าขายรวม: %.2f บาท\n" +
                        "   - ต้นทุนรวม: %.2f บาท\n" +
                        "   - กำไรคาดการณ์: %.2f บาท\n\n",
                products.size(),
                totalSellingPrice,
                totalCost,
                totalSellingPrice - totalCost
        ));

        // แสดงสินค้า Top 5
        context.append("🏆 สินค้าราคาสูงสุด Top 5:\n");
        products.stream()
                .sorted((a, b) -> b.getSellingPrice().compareTo(a.getSellingPrice()))
                .limit(5)
                .forEach(p -> {
                    context.append(String.format(
                            "   %d. %s - %.2f บาท\n",
                            products.indexOf(p) + 1,
                            p.getProductName(),
                            p.getSellingPrice()
                    ));
                });

        return geminiService.chat(new ChatRequest(message, context.toString()));
    }

    /**
     * ตรวจสอบ keywords ในข้อความ
     */
    private boolean containsKeywords(String message, String... keywords) {
        String lowerMessage = message.toLowerCase();
        for (String keyword : keywords) {
            if (lowerMessage.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}