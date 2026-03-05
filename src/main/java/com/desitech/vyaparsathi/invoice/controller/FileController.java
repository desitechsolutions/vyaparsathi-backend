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

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger logger = LoggerFactory.getLogger(FileController.class);

    @Autowired(required = false)
    private Storage storage;

    @Value("${spring.file.upload.dir:gs://vyaparsathi_s3_bucket/}")
    private String uploadDir;

    @GetMapping("/display")
    public ResponseEntity<byte[]> displayFile(@RequestParam String path) {
        if (storage == null) {
            logger.error("GCS Storage bean is not initialized.");
            return ResponseEntity.internalServerError().build();
        }

        try {
            // Robust extraction: removes gs:// and takes only the part before the first slash
            String bucketName = uploadDir.replace("gs://", "");
            if (bucketName.contains("/")) {
                bucketName = bucketName.split("/")[0];
            }

            logger.debug("Fetching file from GCS: bucket={}, path={}", bucketName, path);
            Blob blob = storage.get(bucketName, path);

            if (blob == null || !blob.exists()) {
                logger.warn("File not found in GCS: {}", path);
                return ResponseEntity.notFound().build();
            }

            String contentType = blob.getContentType();
            if (contentType == null) {
                // Manual fallback detection
                if (path.toLowerCase().endsWith(".png")) contentType = "image/png";
                else if (path.toLowerCase().endsWith(".jpg") || path.toLowerCase().endsWith(".jpeg")) contentType = "image/jpeg";
                else if (path.toLowerCase().endsWith(".svg")) contentType = "image/svg+xml";
                else contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(blob.getContent());

        } catch (Exception e) {
            logger.error("Error displaying file from GCS: {}", path, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}