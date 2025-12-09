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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
     * ⭐ เรียก Gemini API (รองรับทั้ง Text-Only และ Text+Image)
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

            // ⭐ เพิ่ม image เฉพาะเมื่อมี (สำหรับ PDF เท่านั้น)
            if (base64Image != null && !base64Image.trim().isEmpty()) {
                JsonObject imagePart = new JsonObject();
                JsonObject inlineData = new JsonObject();
                inlineData.addProperty("mime_type", "image/png");
                inlineData.addProperty("data", base64Image);
                imagePart.add("inline_data", inlineData);
                parts.add(imagePart);

                System.out.println("📷 Request type: TEXT + IMAGE (PDF)");
            } else {
                System.out.println("📝 Request type: TEXT ONLY (Excel)");
            }

            content.add("parts", parts);
            contents.add(content);
            requestBody.add("contents", contents);

            // เพิ่ม generation config
            JsonObject generationConfig = new JsonObject();
            generationConfig.addProperty("temperature", 0.1); // ต่ำเพื่อความแม่นยำ
            generationConfig.addProperty("maxOutputTokens", 8192); // ⭐ เพิ่มเป็น 8192 สำหรับ Excel
            requestBody.add("generationConfig", generationConfig);

            System.out.println("📤 Sending request to Gemini API...");
            System.out.println("Prompt length: " + prompt.length() + " characters");

            // เรียก API
            String response = webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("📥 Received response from Gemini API");

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

    /**
     * ⭐ Analyze TikTok Excel using Gemini AI (TEXT ONLY)
     */
    public String analyzeTiktokExcelWithGemini(MultipartFile file) throws IOException {

        System.out.println("========== Starting Gemini TikTok Excel Analysis ==========");
        System.out.println("File: " + file.getOriginalFilename());
        System.out.println("Size: " + file.getSize() + " bytes");

        // 1. อ่าน Excel และแปลงเป็น Text
        String excelContent = convertExcelToText(file);

        System.out.println("Excel content length: " + excelContent.length() + " characters");

        // 2. สร้าง Prompt สำหรับ Gemini
        String prompt = createPromptForTiktokExcelExtraction(excelContent);

        System.out.println("Prompt length: " + prompt.length() + " characters");

        // 3. ⭐ เรียก Gemini API โดยไม่ส่ง image (ส่ง null)
        String response = callGeminiAPI(prompt, null);

        System.out.println("========== Gemini Analysis Complete ==========");

        return response;
    }

    /**
     * แปลง Excel เป็น Text Content สำหรับ Gemini
     */
    private String convertExcelToText(MultipartFile file) throws IOException {
        StringBuilder content = new StringBuilder();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheet("Order details");

            if (sheet == null) {
                sheet = workbook.getSheetAt(0);
            }

            // อ่าน Header Row
            Row headerRow = sheet.getRow(0);
            if (headerRow != null) {
                content.append("HEADERS:\n");
                for (Cell cell : headerRow) {
                    content.append(getCellValueAsString(cell)).append("\t");
                }
                content.append("\n\n");
            }

            // อ่าน Data Rows (จำกัด 20 แถวแรก เพื่อไม่ให้ข้อมูลยาวเกินไป)
            content.append("DATA:\n");
            int maxRows = Math.min(sheet.getLastRowNum(), 20);

            for (int i = 1; i <= maxRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                content.append("Row ").append(i).append(": ");
                for (int j = 0; j < headerRow.getLastCellNum(); j++) {
                    Cell cell = row.getCell(j);
                    content.append(getCellValueAsString(cell)).append("\t");
                }
                content.append("\n");
            }
        }

        return content.toString();
    }

    /**
     * ⭐ สร้าง Prompt สำหรับ Gemini AI ให้วิเคราะห์ TikTok Excel (แก้ไขใหม่)
     */
    private String createPromptForTiktokExcelExtraction(String excelContent) {
        return """
        You are an expert in extracting data from TikTok Shop Excel files.
        
        Analyze this Excel data and extract ALL orders with complete information in JSON format.
        
        Excel Data:
        """ + excelContent + """
        
        
        Extract these fields for EACH order:
        - orderNumber: Column 0 (Order/adjustment ID) - This is the PO number
        - orderCreatedTime: Column 2 (Order created time)
        - orderSettledTime: Column 3 (Order settled time)
        - totalRevenue: Column 6 (Total revenue - ยอดรวมก่อนหัก)
        - totalFees: Column 13 (Total fees - ค่าธรรมเนียม/ส่วนลด - usually negative)
        - totalSettlementAmount: Column 5 (Total settlement amount - ยอดสุทธิ)
        - items: From Column 52 (Shopping center items) - Format: "SKU * Quantity;"
        
        IMPORTANT:
        - Each row is ONE order (PO)
        - Extract ALL rows that have Order ID
        - totalFees is usually NEGATIVE (fees/discount)
        - Convert totalFees to POSITIVE for discount display
        - Items format: "1729997094462589879 * 3; 1829997094462589880 * 2;"
        
        Output format for MULTIPLE orders:
        {
          "orders": [
            {
              "orderNumber": "580012697098291059",
              "orderCreatedTime": "2025-08-16 10:30:00",
              "orderSettledTime": "2025-08-21 14:00:00",
              "totalRevenue": "207.00",
              "totalFees": "-39.58",
              "totalSettlementAmount": "167.42",
              "items": [
                {
                  "shoppingCenterItem": "1729997094462589879 * 3",
                  "productName": "Product Name (optional)"
                }
              ]
            },
            {
              "orderNumber": "580012697098291060",
              "orderCreatedTime": "2025-08-16 11:00:00",
              "orderSettledTime": "2025-08-21 15:00:00",
              "totalRevenue": "300.00",
              "totalFees": "-50.00",
              "totalSettlementAmount": "250.00",
              "items": [
                {
                  "shoppingCenterItem": "1829997094462589880 * 2"
                }
              ]
            }
          ]
        }
        
        For single order, use the same format but with one order in array.
        
        Rules:
        - Dates: yyyy-MM-dd HH:mm:ss or yyyy/MM/dd
        - Prices: 2 decimal places, as strings
        - Convert negative totalFees to positive if needed
        - Return JSON ONLY (no markdown, no explanations)
        - Include ALL orders found in the Excel
        """;
    }

    /**
     * Get Cell value as String
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numValue = cell.getNumericCellValue();
                    // ถ้าเป็นเลขจำนวนเต็ม ไม่แสดงทศนิยม
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    }
                    return String.valueOf(numValue);
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            default:
                return "";
        }
    }
}