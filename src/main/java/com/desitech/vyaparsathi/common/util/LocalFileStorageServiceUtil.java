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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Profile("local")
public class LocalFileStorageServiceUtil implements FileStorageService {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageServiceUtil.class);

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
            // Store under root uploads/ directory for dynamic, live local serving without compilation restarts
            Path targetDir = Paths.get("uploads", folder).toAbsolutePath().normalize();

            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
                log.info("Created directory: {}", targetDir);
            }

            Path filePath = targetDir.resolve(fileName);
            Files.write(filePath, file.getBytes());
            log.info("File stored locally at: {}", filePath);

            // Construct the path (e.g. logos/filename.png)
            return folder + "/" + fileName;
        } catch (IOException e) {
            log.error("Failed to store file locally: folder={}, fileName={}", folder, fileName, e);
            throw new IOException("Failed to store file locally: " + e.getMessage(), e);
        }
    }
}