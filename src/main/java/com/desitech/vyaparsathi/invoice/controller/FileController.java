package com.desitech.vyaparsathi.invoice.controller;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired(required = false)
    private Storage storage;

    @Value("${spring.file.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping("/display")
    public ResponseEntity<byte[]> displayFile(@RequestParam String path) {
        try {
            // --- 1. TRY CLOUD STORAGE (PROD) ---
            if (storage != null && uploadDir.startsWith("gs://")) {
                String bucketName = uploadDir.replace("gs://", "");
                if (bucketName.contains("/")) {
                    bucketName = bucketName.split("/")[0];
                }

                logger.debug("Cloud Mode: Fetching from GCS bucket: {}, path: {}", bucketName, path);
                Blob blob = storage.get(bucketName, path);

                if (blob != null && blob.exists()) {
                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(determineContentType(path, blob.getContentType())))
                            .body(blob.getContent());
                }
            }

            // --- 2. TRY LOCAL FILE SYSTEM (LOCAL DEV / FALLBACK) ---
            // On local, uploadDir will be something like "uploads/"
            Path localFilePath = Paths.get("src/main/resources/static", uploadDir).resolve(path).normalize();

            if (Files.exists(localFilePath)) {
                logger.debug("Local Mode: Fetching from disk: {}", localFilePath);
                byte[] fileBytes = Files.readAllBytes(localFilePath);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(determineContentType(path, null)))
                        .body(fileBytes);
            }

            logger.warn("File not found in Cloud or Local: {}", path);
            return ResponseEntity.notFound().build();

        } catch (Exception e) {
            logger.error("Error processing file request for path: {}", path, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Helper to determine content type based on file extension or GCS metadata
     */
    private String determineContentType(String path, String gcsContentType) {
        if (gcsContentType != null) return gcsContentType;

        String lowerPath = path.toLowerCase();
        if (lowerPath.endsWith(".png")) return "image/png";
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return "image/jpeg";
        if (lowerPath.endsWith(".svg")) return "image/svg+xml";
        if (lowerPath.endsWith(".pdf")) return "application/pdf";

        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}