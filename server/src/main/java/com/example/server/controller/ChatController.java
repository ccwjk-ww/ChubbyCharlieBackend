package com.example.server.controller;

import com.example.server.dto.ChatRequest;
import com.example.server.dto.ChatResponse;
import com.example.server.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final GeminiService geminiService;

    /**
     * Endpoint สำหรับ chat
     */
    @PostMapping
    public Mono<ResponseEntity<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return geminiService.chat(request)
                .map(ResponseEntity::ok)
                .onErrorResume(error ->
                        Mono.just(ResponseEntity.internalServerError()
                                .body(ChatResponse.error(error.getMessage())))
                );
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Chat AI is running!");
    }
}