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
            String prompt = buildPrompt(request);
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

    private String buildPrompt(ChatRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("คุณคือผู้ช่วย AI สำหรับระบบจัดการสต็อก Chubby Charlie.\n");
        sb.append("คุณช่วยตอบคำถามเกี่ยวกับสินค้า คำสั่งซื้อ และการพยากรณ์สต็อกได้.\n\n");
        if (request.getContext() != null && !request.getContext().isEmpty()) {
            sb.append("ข้อมูลเพิ่มเติม:\n").append(request.getContext()).append("\n\n");
        }
        sb.append("คำถาม: ").append(request.getMessage());
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

