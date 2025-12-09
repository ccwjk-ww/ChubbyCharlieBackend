package com.example.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class GeminiStockForecastService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final WebClient webClient;
    private final Gson gson;

    public GeminiStockForecastService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.gson = new Gson();
    }

    /**
     * ⭐ ใช้ Gemini AI วิเคราะห์ Stock Pattern และคาดการณ์
     */
    public String analyzeStockForecast(StockForecastAnalysisRequest request) {
        try {
            String prompt = buildStockAnalysisPrompt(request);

            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject content = new JsonObject();
            JsonArray parts = new JsonArray();

            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            parts.add(textPart);

            content.add("parts", parts);
            contents.add(content);
            requestBody.add("contents", contents);

            // ตั้งค่า generation config
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.2); // ต่ำเพื่อความแม่นยำ
            generationConfig.addProperty("maxOutputTokens", 2048);
            requestBody.add("generationConfig", generationConfig);

            System.out.println("📤 Sending stock analysis request to Gemini AI...");

            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return extractTextFromGeminiResponse(response);

        } catch (Exception e) {
            System.err.println("❌ Error calling Gemini API: " + e.getMessage());
            throw new RuntimeException("Failed to analyze stock forecast: " + e.getMessage(), e);
        }
    }

    /**
     * ⭐ สร้าง Prompt ที่ชัดเจนสำหรับการวิเคราะห์ Stock
     */
    private String buildStockAnalysisPrompt(StockForecastAnalysisRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("คุณเป็นผู้เชี่ยวชาญด้านการจัดการสต็อกและ Supply Chain Management\n\n");

        prompt.append("=== ข้อมูล Stock Item ===\n");
        prompt.append("ชื่อสินค้า: ").append(request.getStockItemName()).append("\n");
        prompt.append("ประเภท: ").append(request.getStockType()).append("\n");
        prompt.append("Stock ปัจจุบัน: ").append(request.getCurrentStock()).append(" ชิ้น\n");
        prompt.append("มูลค่า Stock: ฿").append(request.getCurrentStockValue()).append("\n\n");

        prompt.append("=== ข้อมูลการใช้งานย้อนหลัง ").append(request.getAnalysisBaseDays()).append(" วัน ===\n");
        prompt.append("การใช้งานเฉลี่ยต่อวัน: ").append(request.getAverageDailyUsage()).append(" ชิ้น\n");
        prompt.append("การใช้งานเฉลี่ยต่อสัปดาห์: ").append(request.getAverageWeeklyUsage()).append(" ชิ้น\n");
        prompt.append("การใช้งานเฉลี่ยต่อเดือน: ").append(request.getAverageMonthlyUsage()).append(" ชิ้น\n");

        if (request.getUsageHistory() != null && !request.getUsageHistory().isEmpty()) {
            prompt.append("\n=== รูปแบบการใช้งานรายวัน (7 วันล่าสุด) ===\n");
            request.getUsageHistory().stream()
                    .limit(7)
                    .forEach(usage -> prompt.append("- ")
                            .append(usage.getDate())
                            .append(": ")
                            .append(usage.getQuantity())
                            .append(" ชิ้น\n"));
        }

        prompt.append("\n=== การตั้งค่า ===\n");
        prompt.append("Safety Stock: ").append(request.getSafetyStockDays()).append(" วัน\n");
        prompt.append("Lead Time: ").append(request.getLeadTimeDays()).append(" วัน\n\n");

        prompt.append("กรุณาวิเคราะห์และให้คำแนะนำในรูปแบบ JSON ดังนี้:\n");
        prompt.append("{\n");
        prompt.append("  \"analysis\": \"การวิเคราะห์รูปแบบการใช้งานและแนวโน้ม\",\n");
        prompt.append("  \"trend\": \"INCREASING | STABLE | DECREASING\",\n");
        prompt.append("  \"trendConfidence\": 0-100,\n");
        prompt.append("  \"seasonalPattern\": \"มีรูปแบบตามฤดูกาลหรือไม่\",\n");
        prompt.append("  \"predictedDailyUsage\": จำนวนที่คาดการณ์ต่อวัน,\n");
        prompt.append("  \"recommendedOrderQuantity\": จำนวนที่แนะนำให้สั่ง,\n");
        prompt.append("  \"urgencyLevel\": \"LOW | MEDIUM | HIGH | CRITICAL\",\n");
        prompt.append("  \"urgencyReason\": \"เหตุผลของระดับความเร่งด่วน\",\n");
        prompt.append("  \"optimalReorderPoint\": จุดที่ควรสั่งซื้อใหม่,\n");
        prompt.append("  \"riskFactors\": [\"ปัจจัยเสี่ยง 1\", \"ปัจจัยเสี่ยง 2\"],\n");
        prompt.append("  \"recommendations\": [\"คำแนะนำ 1\", \"คำแนะนำ 2\"],\n");
        prompt.append("  \"costImpact\": \"ผลกระทบต่อต้นทุน\",\n");
        prompt.append("  \"actionItems\": [\n");
        prompt.append("    {\"priority\": \"HIGH|MEDIUM|LOW\", \"action\": \"สิ่งที่ควรทำ\", \"timeline\": \"กรอบเวลา\"}\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");

        prompt.append("⚠️ สำคัญ: ตอบเป็น JSON เท่านั้น ไม่ต้องมีคำอธิบายเพิ่มเติมหรือ markdown");

        return prompt.toString();
    }

    /**
     * แยก text จาก Gemini response
     */
    private String extractTextFromGeminiResponse(String response) {
        try {
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);
            JsonArray candidates = jsonResponse.getAsJsonArray("candidates");

            if (candidates != null && candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");

                if (parts != null && parts.size() > 0) {
                    JsonObject part = parts.get(0).getAsJsonObject();
                    String text = part.get("text").getAsString();

                    // ลบ markdown code block
                    text = text.replaceAll("```json\\s*", "")
                            .replaceAll("```\\s*", "")
                            .trim();

                    return text;
                }
            }

            throw new RuntimeException("No valid response from Gemini API");

        } catch (Exception e) {
            System.err.println("❌ Error parsing Gemini response: " + e.getMessage());
            throw new RuntimeException("Failed to parse Gemini response", e);
        }
    }

    /**
     * ⭐ วิเคราะห์หลาย Stock Items พร้อมกัน
     */
    public String analyzeBulkStockForecast(List<StockForecastAnalysisRequest> requests) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("คุณเป็นผู้เชี่ยวชาญด้านการจัดการสต็อกและ Supply Chain Management\n\n");
        prompt.append("กรุณาวิเคราะห์ Stock Items ทั้งหมด ").append(requests.size()).append(" รายการ ");
        prompt.append("และจัดลำดับความสำคัญในการสั่งซื้อ\n\n");

        for (int i = 0; i < requests.size(); i++) {
            StockForecastAnalysisRequest req = requests.get(i);
            prompt.append("=== Stock Item #").append(i + 1).append(" ===\n");
            prompt.append("ชื่อ: ").append(req.getStockItemName()).append("\n");
            prompt.append("Stock ปัจจุบัน: ").append(req.getCurrentStock()).append(" ชิ้น\n");
            prompt.append("ใช้เฉลี่ยต่อวัน: ").append(req.getAverageDailyUsage()).append(" ชิ้น\n");
            prompt.append("คาดว่าจะหมดใน: ").append(
                    req.getCurrentStock() / Math.max(1, req.getAverageDailyUsage())
            ).append(" วัน\n\n");
        }

        prompt.append("กรุณาให้คำแนะนำในรูปแบบ JSON:\n");
        prompt.append("{\n");
        prompt.append("  \"overallAssessment\": \"การประเมินภาพรวม\",\n");
        prompt.append("  \"criticalItems\": [\"รายการที่ต้องสั่งเร่งด่วน\"],\n");
        prompt.append("  \"orderPriority\": [\n");
        prompt.append("    {\"stockItemName\": \"ชื่อ\", \"priority\": 1-10, \"reason\": \"เหตุผล\"}\n");
        prompt.append("  ],\n");
        prompt.append("  \"budgetRecommendation\": \"คำแนะนำเรื่องงบประมาณ\",\n");
        prompt.append("  \"riskMitigation\": [\"แนวทางลดความเสี่ยง\"]\n");
        prompt.append("}\n\n");
        prompt.append("⚠️ สำคัญ: ตอบเป็น JSON เท่านั้น");

        // เรียก Gemini API (คล้ายกับ method analyzeStockForecast)
        // ... implementation ...

        return ""; // placeholder
    }

    /**
     * ⭐ Request DTO
     */
    @lombok.Data
    public static class StockForecastAnalysisRequest {
        private String stockItemName;
        private String stockType;
        private Integer currentStock;
        private java.math.BigDecimal currentStockValue;
        private Integer averageDailyUsage;
        private Integer averageWeeklyUsage;
        private Integer averageMonthlyUsage;
        private Integer safetyStockDays;
        private Integer leadTimeDays;
        private Integer analysisBaseDays;
        private List<UsageHistoryPoint> usageHistory;

        @lombok.Data
        public static class UsageHistoryPoint {
            private String date;
            private Integer quantity;
        }
    }
}