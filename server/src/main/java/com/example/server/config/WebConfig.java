package com.example.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${file.upload-dir:uploads/products}")
    private String uploadDir;

    /**
     * ⭐ Config เพื่อให้ Spring Boot serve รูปภาพจาก directory ที่อัปโหลด
     *
     * เช่น: http://localhost:8080/uploads/products/abc-123.jpg
     * จะ map ไปที่ไฟล์ใน uploads/products/abc-123.jpg
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve uploaded files
        registry.addResourceHandler("/uploads/products/**")
                .addResourceLocations("file:" + uploadDir + "/");

        // Serve default images from resources
        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");
    }
}