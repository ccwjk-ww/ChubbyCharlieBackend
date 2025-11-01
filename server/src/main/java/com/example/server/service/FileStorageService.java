package com.example.server.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;

@Service
public class FileStorageService {

    @Value("${file.upload-dir:uploads/products}")
    private String uploadDir;

    private final String defaultImage = "logo.jpg"; // ✅ รูป default

    // บันทึกไฟล์รูป
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return defaultImage;
        }

        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return fileName;
        } catch (IOException e) {
            e.printStackTrace();
            return defaultImage;
        }
    }

    public String getDefaultImage() {
        return defaultImage;
    }

    public Path getFilePath(String fileName) {
        return Paths.get(uploadDir).resolve(fileName);
    }

    public Resource loadFileAsResource(String fileName) {
        try {
            Path filePath = getFilePath(fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                return new UrlResource(getFilePath(defaultImage).toUri());
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
