package com.example.server.config;// com.example.server.config.WebConfig.java
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class WebConfigObjectMapper {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS); // บังคับให้ส่งทุกฟิลด์
        return objectMapper;
    }
}