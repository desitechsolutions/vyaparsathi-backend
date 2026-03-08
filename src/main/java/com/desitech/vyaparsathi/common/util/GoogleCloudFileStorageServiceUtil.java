package com.desitech.vyaparsathi.common.util;

import com.google.cloud.storage.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Profile("prod")
public class GoogleCloudFileStorageServiceUtil implements FileStorageService {

    private final Storage storage;

    @Value("${spring.file.upload.dir}")
    private String uploadDir;

    public GoogleCloudFileStorageServiceUtil(Storage storage) {
        this.storage = storage;
    }

    @Override
    public String storeFile(MultipartFile file, String folder, UUID userId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String fileName = userId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        return storeFileInGCS(file, folder, fileName);
    }

    private String storeFileInGCS(MultipartFile file, String folder, String fileName) throws IOException {
        //extract bucket name from "gs://bucket-name/"
        String bucketName = uploadDir.replace("gs://", "").split("/")[0];
        String objectPath = folder + "/" + fileName;

        try {
            BlobId blobId = BlobId.of(bucketName, objectPath);

            // Setting Content-Type is important so the browser/PDF generator knows it's an image
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();

            storage.create(blobInfo, file.getBytes());

            log.info("File stored in GCS at: {}/{}", bucketName, objectPath);

            // Return ONLY the path (e.g., "logos/uuid_timestamp_techie.jpeg")
            // This is what will be saved in shop.logo_path or shop.signature_path
            return objectPath;
        } catch (StorageException e) {
            log.error("GCS Upload failed", e);
            throw new RuntimeException("Failed to store file in GCS: " + e.getMessage(), e);
        }
    }
}