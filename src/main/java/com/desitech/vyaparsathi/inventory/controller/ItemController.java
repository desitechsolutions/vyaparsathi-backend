
package com.desitech.vyaparsathi.inventory.controller;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.desitech.vyaparsathi.inventory.dto.ItemDto;
import com.desitech.vyaparsathi.inventory.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog")
public class ItemController {

    private static final Logger logger = LoggerFactory.getLogger(ItemController.class);

    @Autowired
    private ItemService itemService;

    @GetMapping
    public List<ItemDto> getAllItems() {
        try {
            List<ItemDto> items = itemService.getAllItems();
            logger.info("Fetched all catalog items");
            return items;
        } catch (Exception e) {
            logger.error("Error fetching all catalog items: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to fetch catalog items", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemDto> getItemById(@PathVariable Long id) {
        try {
            ItemDto item = itemService.getItemById(id);
            logger.info("Fetched catalog item id={}", id);
            return ResponseEntity.ok(item);
        } catch (Exception e) {
            logger.error("Error fetching catalog item id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to fetch catalog item", e);
        }
    }

    @PostMapping(consumes = { "multipart/form-data" })
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ItemDto> createItem(
            @Valid @RequestPart("itemDto") ItemDto itemDto,
            @RequestParam(required = false) Map<String, MultipartFile> photos) {
        try {
            if (photos != null && !photos.isEmpty()) {
                for (Map.Entry<String, MultipartFile> entry : photos.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("variant_photo_")) {
                        try {
                            int variantIndex = Integer.parseInt(key.substring("variant_photo_".length()));
                            if (variantIndex >= 0 && variantIndex < itemDto.getVariants().size()) {
                                String photoPath = savePhoto(entry.getValue());
                                itemDto.getVariants().get(variantIndex).setPhotoPath(photoPath);
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Received photo with malformed key in createItem: {}", key);
                        }
                    }
                }
            }

            ItemDto created = itemService.createItem(itemDto);
            logger.info("Created catalog item with name={}", itemDto.getName());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            logger.error("Error creating catalog item: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to create catalog item", e);
        }
    }

    private String savePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return null;
        }

        // HARDENED LOGIC:
        // 1. Sanitize the original filename to prevent path traversal attacks.
        String originalFileName = StringUtils.cleanPath(photo.getOriginalFilename());
        if (originalFileName.contains("..")) {
            throw new ApplicationException("Invalid file name: " + originalFileName);
        }

        // 2. Generate a unique filename to prevent overwrites.
        String fileExtension = "";
        int lastDot = originalFileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileExtension = originalFileName.substring(lastDot); // e.g., ".jpg"
        }
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;

        // It's better to configure this path in application.properties
        Path uploadDir = Paths.get("uploads/item-photos/");
        Path destinationPath = uploadDir.resolve(uniqueFileName);

        try {
            Files.createDirectories(uploadDir);
            Files.write(destinationPath, photo.getBytes());
            logger.info("Saved photo {} to {}", uniqueFileName, destinationPath);

            // Return a path that can be used by the web server, not the full system path
            return "/media/item-photos/" + uniqueFileName; // Example web-accessible path
        } catch (IOException e) {
            logger.error("Error saving photo {}: {}", uniqueFileName, e.getMessage(), e);
            throw new ApplicationException("Failed to save photo", e);
        }
    }
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<List<ItemDto>> createItems(@Valid @RequestBody List<ItemDto> items) {
        try {
            List<ItemDto> created = itemService.createItems(items);
            logger.info("Bulk created catalog items, count={}", created.size());
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            logger.error("Error bulk creating catalog items: {}", e.getMessage(), e);
            throw new ApplicationException("Failed to bulk create catalog items", e);
        }
    }
    @PutMapping(value = "/{id}", consumes = { "multipart/form-data" })
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ItemDto> updateItem(
            @PathVariable Long id,
            @Valid @RequestPart("itemDto") ItemDto itemDto,
            @RequestParam(required = false) Map<String, MultipartFile> photos) {
        try {
            if (photos != null && !photos.isEmpty()) {
                for (Map.Entry<String, MultipartFile> entry : photos.entrySet()) {
                    String key = entry.getKey(); // e.g., "variant_photo_0"
                    MultipartFile file = entry.getValue();

                    // Check if the key matches the expected pattern
                    if (key.startsWith("variant_photo_")) {
                        try {
                            // Extract the index from the key
                            int variantIndex = Integer.parseInt(key.substring("variant_photo_".length()));

                            // Ensure the index is valid for the variants list
                            if (variantIndex >= 0 && variantIndex < itemDto.getVariants().size()) {
                                String photoPath = savePhoto(file); // Your existing savePhoto method
                                // Directly set the path on the correct variant
                                itemDto.getVariants().get(variantIndex).setPhotoPath(photoPath);
                            } else {
                                logger.warn("Received photo with out-of-bounds index: {}", key);
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Received photo with malformed key: {}", key);
                        }
                    }
                }
            }

            ItemDto updated = itemService.updateItem(id, itemDto);
            logger.info("Updated catalog item id={}", id);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            logger.error("Error updating catalog item id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to update catalog item", e);
        }
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Void> deleteItemVariant(@PathVariable Long id) {
        try {
            itemService.deleteItemVariant(id);
            logger.info("Deleted catalog item variants id={}", id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.error("Error deleting catalog item variant id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to delete catalog item variant", e);
        }
    }
}
