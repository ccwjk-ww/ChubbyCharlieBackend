package com.example.server.controller;

import com.example.server.dto.ChatRequest;
import com.example.server.dto.ChatResponse;
import com.example.server.service.SmartChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final SmartChatService smartChatService;

    /**
     * ⭐ Endpoint สำหรับ Smart Chat (มี context จากระบบ)
     */
    @PostMapping
    public Mono<ResponseEntity<ChatResponse>> chat(@RequestBody ChatRequest request) {
        return smartChatService.smartChat(request.getMessage())
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
        return ResponseEntity.ok("Chat AI is running! 🤖✅");
    }
}