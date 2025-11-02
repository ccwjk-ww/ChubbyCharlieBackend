package com.example.server.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class GeminiAIService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final WebClient webClient;
    private final Gson gson;

    public GeminiAIService() {
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.gson = new Gson();
    }

    /**
     * แปลง PDF เป็น Base64 images และส่งให้ Gemini วิเคราะห์
     */
    public String analyzePDFWithGemini(MultipartFile pdfFile) throws IOException {
        // 1. แปลง PDF เป็น images (Base64)
        String base64Image = convertPDFToBase64Image(pdfFile);

        // 2. สร้าง prompt สำหรับ Gemini
        String prompt = createPromptForOrderExtraction();

        // 3. เรียก Gemini API
        return callGeminiAPI(prompt, base64Image);
    }

    /**
     * แปลง PDF page แรกเป็น Base64 image
     */
    private String convertPDFToBase64Image(MultipartFile pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile.getInputStream())) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            // Render หน้าแรก (index 0) ที่ DPI 300 เพื่อความคมชัด
            BufferedImage image = pdfRenderer.renderImageWithDPI(0, 300);

            // แปลงเป็น Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);
        }
    }

    /**
     * สร้าง prompt ที่ชัดเจนสำหรับการดึงข้อมูล
     */
    private String createPromptForOrderExtraction() {
        return """
                คุณเป็นผู้เชี่ยวชาญในการอ่านและแยกข้อมูลจากใบสั่งซื้อ (Purchase Order) ของบริษัท ทเวนตี้โฟร์ ช้อปปิ้ง จำกัด
                
                กรุณาวิเคราะห์เอกสารนี้และดึงข้อมูลรายการสินค้าทั้งหมดออกมาในรูปแบบ JSON Array
                
                สำหรับแต่ละรายการสินค้า ให้ดึงข้อมูลดังนี้:
                - productSku: รหัสสินค้า 6 หลัก (คอลัมน์ที่ 2)
                - productName: ชื่อสินค้า (ข้อความหลัง SKU 8 หลัก)
                - quantity: ปริมาณ/จำนวนหีบ (คอลัมน์ปริมาณขนาดบรรจุ หีบ/หน่วย)
                - unitPrice: ราคาต่อหน่วย (คอลัมน์ราคาต่อหีบ/หน่วย)
                - totalPrice: จำนวนเงิน (คอลัมน์สุดท้าย)
                
                ตัวอย่างรูปแบบ output ที่ต้องการ:
                {
                  "items": [
                    {
                      "productSku": "563337",
                      "productName": "ถุงเท้าสีดำข้อสั้น แพ็ค 12 คู่(1x1)",
                      "quantity": 500,
                      "unitPrice": 60.00,
                      "totalPrice": 30000.00
                    }
                  ]
                }
                
                หมายเหตุสำคัญ:
                1. ดึงเฉพาะข้อมูลจากตาราง (ไม่ต้องดึงข้อมูล header, footer, หรือเงื่อนไขต่างๆ)
                2. quantity ต้องเป็นตัวเลขจำนวนเต็ม
                3. unitPrice และ totalPrice ต้องเป็นทศนิยม 2 ตำแหน่ง
                4. ถ้ามีหลายหน้า ให้ดึงข้อมูลทุกหน้า
                5. ตอบกลับเป็น JSON เท่านั้น ไม่ต้องมีคำอธิบายเพิ่มเติม
                """;
    }

    /**
     * เรียก Gemini API
     */
    private String callGeminiAPI(String prompt, String base64Image) {
        try {
            // สร้าง request body ตาม Gemini API format
            JsonObject requestBody = new JsonObject();
            JsonArray contents = new JsonArray();
            JsonObject content = new JsonObject();
            JsonArray parts = new JsonArray();

            // เพิ่ม text prompt
            JsonObject textPart = new JsonObject();
            textPart.addProperty("text", prompt);
            parts.add(textPart);

            // เพิ่ม image
            JsonObject imagePart = new JsonObject();
            JsonObject inlineData = new JsonObject();
            inlineData.addProperty("mime_type", "image/png");
            inlineData.addProperty("data", base64Image);
            imagePart.add("inline_data", inlineData);
            parts.add(imagePart);

            content.add("parts", parts);
            contents.add(content);
            requestBody.add("contents", contents);

            // เพิ่ม generation config
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.1); // ต่ำเพื่อความแม่นยำ
            generationConfig.addProperty("maxOutputTokens", 4096);
            requestBody.add("generationConfig", generationConfig);

            System.out.println("📤 Sending request to Gemini API...");
            System.out.println("Request body: " + requestBody.toString());

            // เรียก API
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("📥 Received response from Gemini API");
            System.out.println("Response: " + response);

            // Parse response
            return extractTextFromGeminiResponse(response);

        } catch (Exception e) {
            System.err.println("❌ Error calling Gemini API: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to call Gemini API: " + e.getMessage(), e);
        }
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

                    // ลบ markdown code block ออก (ถ้ามี)
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
}