//package com.example.server.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class WebConfig implements WebMvcConfigurer {
//
//    @Value("${file.upload-dir:uploads/products}")
//    private String uploadDir;
//
//    /**
//     * ⭐ Config เพื่อให้ Spring Boot serve รูปภาพจาก directory ที่อัปโหลด
//     *
//     * เช่น: http://localhost:8080/uploads/products/abc-123.jpg
//     * จะ map ไปที่ไฟล์ใน uploads/products/abc-123.jpg
//     */
//    @Override
//    public void addResourceHandlers(ResourceHandlerRegistry registry) {
//        // Serve uploaded files
//        registry.addResourceHandler("/uploads/products/**")
//                .addResourceLocations("file:" + uploadDir + "/");
//
//        // Serve default images from resources
//        registry.addResourceHandler("/images/**")
//                .addResourceLocations("classpath:/static/images/");
//    }
//}
package com.example.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * ⭐ Path สำหรับเก็บรูปภาพใน VPS
     * Default: /var/www/images/products/
     * สามารถ override ได้ใน application.properties
     */
    @Value("${file.upload-dir:/var/www/images/products}")
    private String uploadDir;

    /**
     * ⭐ Config เพื่อให้ Spring Boot serve รูปภาพจาก /var/www/images/
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // ตรวจสอบว่า directory มีอยู่จริง ถ้าไม่มีให้สร้าง
        File uploadDirectory = new File(uploadDir);
        if (!uploadDirectory.exists()) {
            boolean created = uploadDirectory.mkdirs();
            System.out.println(created ?
                    "✅ Created upload directory: " + uploadDir :
                    "⚠️ Failed to create upload directory: " + uploadDir);
        }

        // 1. Serve รูปภาพที่ upload จาก /var/www/images/products/
        registry.addResourceHandler("/images/products/**")
                .addResourceLocations("file:" + uploadDir + "/");

        // 2. Serve default images (logo.jpg) จาก /var/www/images/products/
        registry.addResourceHandler("/images/**")
                .addResourceLocations("file:/var/www/images/");

        // 3. Serve static resources จาก classpath (backup)
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        System.out.println("✅ Static resource mapping configured:");
        System.out.println("   /images/products/** → file:" + uploadDir + "/");
        System.out.println("   /images/** → file:/var/www/images/");
    }
}