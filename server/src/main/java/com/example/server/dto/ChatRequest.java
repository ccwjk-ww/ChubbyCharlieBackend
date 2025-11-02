package com.example.server.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String message;
    private String context; // Optional: ข้อมูลเพิ่มเติม เช่น product info
}