package com.example.server.service;

import com.example.server.dto.*;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final WebClient webClient;
    private final Gson gson = new Gson();

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public Mono<ChatResponse> chat(ChatRequest request) {
        try {
            String prompt = buildEnhancedPrompt(request);
            GeminiRequest geminiRequest = createGeminiRequest(prompt);

            System.out.println("Gemini URL: " + apiUrl + "?key=" + apiKey);
            System.out.println("Gemini Request JSON: " + gson.toJson(geminiRequest));

            return webClient.post()
                    .uri(apiUrl + "?key=" + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(geminiRequest)
                    .retrieve()
                    .bodyToMono(GeminiResponse.class)
                    .map(this::extractResponse)
                    .map(result -> ChatResponse.success(result, UUID.randomUUID().toString()))
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        String msg = ex.getStatusCode() + " - " + ex.getResponseBodyAsString();
                        System.err.println("Gemini API Error: " + msg);
                        return Mono.just(ChatResponse.error("Gemini API Error: " + msg));
                    })
                    .onErrorResume(Exception.class, ex -> {
                        ex.printStackTrace();
                        return Mono.just(ChatResponse.error("Unexpected error: " + ex.getMessage()));
                    });

        } catch (Exception e) {
            e.printStackTrace();
            return Mono.just(ChatResponse.error(e.getMessage()));
        }
    }

    /**
     * ⭐ สร้าง Prompt ที่ดีขึ้น พร้อมคำแนะนำในการตอบ
     */
    private String buildEnhancedPrompt(ChatRequest request) {
        StringBuilder sb = new StringBuilder();

        // System Prompt
        sb.append("คุณคือ **Chubby AI Assistant** ผู้ช่วยระบบจัดการสต็อกอัจฉริยะสำหรับธุรกิจ Chubby Charlie\n\n");

        sb.append("**บทบาทและความสามารถของคุณ:**\n");
        sb.append("- 📊 วิเคราะห์ข้อมูลสินค้า สต็อก และยอดขายอย่างละเอียด\n");
        sb.append("- 💡 ให้คำแนะนำเชิงธุรกิจที่เป็นประโยชน์\n");
        sb.append("- ⚠️ เตือนปัญหาที่ต้องแก้ไขอย่างชัดเจน\n");
        sb.append("- 📈 สรุปข้อมูลแบบกระชับและเข้าใจง่าย\n");
        sb.append("- 🎯 ตอบคำถามอย่างตรงประเด็นและเป็นมิตร\n\n");

        sb.append("**รูปแบบการตอบ:**\n");
        sb.append("1. ใช้ภาษาไทยที่เข้าใจง่าย เป็นกันเอง แต่เป็นมืออาชีพ\n");
        sb.append("2. ใช้ emoji ที่เหมาะสมเพื่อให้อ่านง่าย เช่น 📊 💰 ⚠️ ✅\n");
        sb.append("3. แบ่งหัวข้อด้วย **หัวข้อหลัก** และใช้ bullet points\n");
        sb.append("4. ตัวเลขให้แสดงหน่วยเงินที่ชัดเจน (เช่น 1,234.56 บาท)\n");
        sb.append("5. หากมีปัญหา ให้เตือนชัดเจนและแนะนำวิธีแก้\n");
        sb.append("6. ถ้าข้อมูลไม่เพียงพอ บอกตรงๆว่าต้องการข้อมูลอะไรเพิ่ม\n\n");

        // Context Data
        if (request.getContext() != null && !request.getContext().isEmpty()) {
            sb.append("**ข้อมูลจากระบบ:**\n");
            sb.append(request.getContext());
            sb.append("\n\n");
        }

        // User Question
        sb.append("**คำถามจากผู้ใช้:**\n");
        sb.append(request.getMessage());
        sb.append("\n\n");

        sb.append("โปรดวิเคราะห์ข้อมูลและตอบคำถามอย่างละเอียด ชัดเจน และเป็นประโยชน์");

        return sb.toString();
    }

    private GeminiRequest createGeminiRequest(String prompt) {
        GeminiRequest.Part part = new GeminiRequest.Part(prompt);
        GeminiRequest.Content content = new GeminiRequest.Content(List.of(part));
        return new GeminiRequest(List.of(content));
    }

    private String extractResponse(GeminiResponse response) {
        if (response.getCandidates() != null && !response.getCandidates().isEmpty()) {
            GeminiResponse.Candidate candidate = response.getCandidates().get(0);
            if (candidate.getContent() != null &&
                    candidate.getContent().getParts() != null &&
                    !candidate.getContent().getParts().isEmpty()) {
                return candidate.getContent().getParts().get(0).getText();
            }
        }
        return "ขออภัย ไม่สามารถสร้างคำตอบได้ในขณะนี้ 😔";
    }
}