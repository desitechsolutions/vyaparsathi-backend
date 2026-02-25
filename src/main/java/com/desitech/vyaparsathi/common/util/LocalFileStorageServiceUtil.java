package com.desitech.vyaparsathi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
@Profile("local")
public class LocalFileStorageServiceUtil implements FileStorageService {

    @Value("${spring.file.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Override
    public String storeFile(MultipartFile file, String folder, UUID userId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String fileName = userId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        return storeFileLocally(file, folder, fileName);
    }

    private String storeFileLocally(MultipartFile file, String folder, String fileName) throws IOException {
        try {
            // Use a path under src/main/resources/static/ for static serving
            Path targetDir = Paths.get("src/main/resources/static", uploadDir, folder).toAbsolutePath().normalize();

            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
                log.info("Created directory: {}", targetDir);
            }

            Path filePath = targetDir.resolve(fileName);
            Files.write(filePath, file.getBytes());
            log.info("File stored locally at: {}", filePath);

            // Construct the public URL
            String publicUrl = String.format("%s/%s/%s/%s", baseUrl, uploadDir, folder, fileName);
            log.info("Returning public URL: {}", publicUrl);
            return publicUrl;
        } catch (IOException e) {
            log.error("Failed to store file locally: uploadDir={}, folder={}, fileName={}", uploadDir, folder, fileName, e);
            throw new IOException("Failed to store file locally: " + e.getMessage(), e);
        }
    }
}