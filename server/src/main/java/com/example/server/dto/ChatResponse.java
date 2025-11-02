package com.example.server.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String conversationId;
    private boolean success;
    private String error;

    public static ChatResponse success(String response, String conversationId) {
        return new ChatResponse(response, conversationId, true, null);
    }

    public static ChatResponse error(String error) {
        return new ChatResponse(null, null, false, error);
    }
}