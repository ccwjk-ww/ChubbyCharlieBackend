package com.example.server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@RestController
@RequestMapping("/images/products")
@CrossOrigin(origins = "*")
public class ImageController {

    @Value("${file.upload-dir:uploads/products}")
    private String uploadDir;

    /**
     * ⭐ Serve logo.jpg (default image)
     */
    @GetMapping("/logo.jpg")
    public ResponseEntity<Resource> getDefaultLogo() {
        return getProductImage("logo.jpg");
    }

    /**
     * ⭐ Serve any product image
     * ลำดับการหา:
     * 1. uploads/products/{filename}
     * 2. static/images/products/{filename}
     * 3. ถ้าหาไม่เจอ return 404
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> getProductImage(@PathVariable String filename) {
        try {
            Resource resource = null;

            // 1. ลองหาจาก uploads directory ก่อน (สำหรับรูปที่ user อัปโหลด)
            File uploadedFile = new File(uploadDir + "/" + filename);
            if (uploadedFile.exists() && uploadedFile.isFile()) {
                System.out.println("✅ Found " + filename + " in uploads/");
                resource = new FileSystemResource(uploadedFile);
                MediaType mediaType = getMediaTypeFromFilename(filename);
                return buildImageResponse(resource, mediaType);
            }

            // 2. ลองหาจาก static resources (สำหรับรูป default เช่น logo.jpg)
            resource = new ClassPathResource("static/images/products/" + filename);
            if (resource.exists()) {
                System.out.println("✅ Found " + filename + " in static/images/products/");
                MediaType mediaType = getMediaTypeFromFilename(filename);
                return buildImageResponse(resource, mediaType);
            }

            // 3. ลองหาจาก root static/images/products (อีก path หนึ่ง)
            resource = new ClassPathResource("images/products/" + filename);
            if (resource.exists()) {
                System.out.println("✅ Found " + filename + " in images/products/");
                MediaType mediaType = getMediaTypeFromFilename(filename);
                return buildImageResponse(resource, mediaType);
            }

            // 4. ถ้าหา logo.jpg ไม่เจอ ลองหารูปอื่นใน uploads มาใช้แทน (fallback)
            if (filename.equals("logo.jpg")) {
                File uploadsDir = new File(uploadDir);
                if (uploadsDir.exists() && uploadsDir.isDirectory()) {
                    File[] imageFiles = uploadsDir.listFiles((dir, name) ->
                            name.toLowerCase().matches(".*\\.(jpg|jpeg|png|gif)$")
                    );

                    if (imageFiles != null && imageFiles.length > 0) {
                        // ใช้รูปแรกที่เจอ
                        File fallbackImage = imageFiles[0];
                        System.out.println("⚠️ logo.jpg not found, using fallback: " + fallbackImage.getName());
                        resource = new FileSystemResource(fallbackImage);
                        MediaType mediaType = getMediaTypeFromFilename(fallbackImage.getName());
                        return buildImageResponse(resource, mediaType);
                    }
                }
            }

            // 5. ไม่เจอเลย
            System.err.println("❌ Image not found: " + filename);
            System.err.println("   Checked locations:");
            System.err.println("   1. " + uploadDir + "/" + filename);
            System.err.println("   2. classpath:static/images/products/" + filename);
            System.err.println("   3. classpath:images/products/" + filename);

            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            System.err.println("❌ Error loading image " + filename + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Helper: Build image response with proper headers
     */
    private ResponseEntity<Resource> buildImageResponse(Resource resource, MediaType mediaType) {
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "public, max-age=3600")
                .header("Access-Control-Allow-Origin", "*")
                .body(resource);
    }

    /**
     * Helper: Get media type from filename
     */
    private MediaType getMediaTypeFromFilename(String filename) {
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();

        switch (extension) {
            case "jpg":
            case "jpeg":
                return MediaType.IMAGE_JPEG;
            case "png":
                return MediaType.IMAGE_PNG;
            case "gif":
                return MediaType.IMAGE_GIF;
            case "webp":
                return MediaType.valueOf("image/webp");
            case "svg":
                return MediaType.valueOf("image/svg+xml");
            default:
                return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}