package com.desitech.vyaparsathi.common.util;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface FileStorageService {
    String storeFile(MultipartFile file, String folder, UUID userId) throws Exception;
}