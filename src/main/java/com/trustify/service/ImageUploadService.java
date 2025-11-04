package com.trustify.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ImageUploadService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public String saveImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("Empty file not allowed");
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path destinationPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        Files.createDirectories(destinationPath);
        Path filePath = destinationPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/listings/" + fileName; // return relative path for frontend display
    }
}
