package com.desitech.vyaparsathi.customer.service;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.util.FileStorageService;
import com.desitech.vyaparsathi.customer.dto.CustomerAttachmentDto;
import com.desitech.vyaparsathi.customer.entity.CustomerAttachment;
import com.desitech.vyaparsathi.customer.repository.CustomerAttachmentRepository;
import com.desitech.vyaparsathi.customer.repository.CustomerRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Uploads and lists customer-scoped file attachments (KYC docs, MSME
 * certificates, contracts). Storage is delegated to {@link FileStorageService};
 * the row here only holds the pointer + display metadata.
 */
@Service
public class CustomerAttachmentService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerAttachmentService.class);

    private final CustomerAttachmentRepository repo;
    private final CustomerRepository customerRepo;
    private final FileStorageService storage;

    public CustomerAttachmentService(CustomerAttachmentRepository repo,
                                     CustomerRepository customerRepo,
                                     FileStorageService storage) {
        this.repo = repo;
        this.customerRepo = customerRepo;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<CustomerAttachmentDto> listByCustomer(Long customerId) {
        ensureCustomerExists(customerId);
        return repo.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public CustomerAttachmentDto upload(Long customerId, MultipartFile file, String category) {
        ensureCustomerExists(customerId);
        if (file == null || file.isEmpty()) {
            throw new ApplicationException("File is required.");
        }
        // Guard: cap at 10 MB per file — matches other document uploads.
        long maxBytes = 10L * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ApplicationException("File too large. Maximum 10 MB.");
        }

        String storedPath;
        try {
            storedPath = storage.storeFile(file, "customer-attachments/" + customerId, UUID.randomUUID());
        } catch (Exception e) {
            logger.error("Failed to store customer attachment: {}", e.getMessage(), e);
            throw new ApplicationException("Could not store the file: " + e.getMessage(), e);
        }

        CustomerAttachment entity = new CustomerAttachment();
        entity.setCustomerId(customerId);
        entity.setFileName(file.getOriginalFilename());
        entity.setFilePath(storedPath);
        entity.setMimeType(file.getContentType());
        entity.setSizeBytes(file.getSize());
        entity.setCategory(normalizeCategory(category));
        entity.setUploadedBy(currentUsername());
        CustomerAttachment saved = repo.save(entity);
        return toDto(saved);
    }

    @Transactional
    public void delete(Long attachmentId) {
        CustomerAttachment existing = repo.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
        // Best-effort file cleanup — never let a stale file block the
        // DB row from being deleted. Storage layer owns the physical
        // path; failure to unlink is a log entry, not a rejection.
        try {
            Path p = Paths.get("uploads").resolve(existing.getFilePath()).toAbsolutePath().normalize();
            Files.deleteIfExists(p);
        } catch (Exception e) {
            logger.warn("Could not remove physical file {} for attachment {}: {}",
                    existing.getFilePath(), attachmentId, e.getMessage());
        }
        repo.delete(existing);
    }

    /**
     * Returns the on-disk absolute path for download by the controller.
     * Only paths that resolve INSIDE the app's uploads root are allowed —
     * a stored path that traverses outside (e.g. via ../) is rejected
     * as a path-traversal guard.
     */
    @Transactional(readOnly = true)
    public Path resolveDownloadPath(Long attachmentId) {
        CustomerAttachment existing = repo.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
        Path root = Paths.get("uploads").toAbsolutePath().normalize();
        Path resolved = root.resolve(existing.getFilePath()).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApplicationException("Refusing to serve a file outside the uploads root.");
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public CustomerAttachment findById(Long attachmentId) {
        return repo.findById(attachmentId)
                .orElseThrow(() -> new EntityNotFoundException("Attachment not found: " + attachmentId));
    }

    private void ensureCustomerExists(Long customerId) {
        if (customerId == null) throw new ApplicationException("customerId is required");
        if (!customerRepo.existsById(customerId)) {
            throw new EntityNotFoundException("Customer not found: " + customerId);
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) return "OTHER";
        String upper = category.trim().toUpperCase();
        // Keep the enum-ish naming stable so the FE can render icons/colors
        // by exact match; anything unknown falls back to OTHER.
        return switch (upper) {
            case "KYC", "CONTRACT", "PO", "INVOICE", "OTHER" -> upper;
            default -> "OTHER";
        };
    }

    private CustomerAttachmentDto toDto(CustomerAttachment e) {
        CustomerAttachmentDto d = new CustomerAttachmentDto();
        d.setId(e.getId());
        d.setCustomerId(e.getCustomerId());
        d.setFileName(e.getFileName());
        d.setFilePath(e.getFilePath());
        d.setMimeType(e.getMimeType());
        d.setSizeBytes(e.getSizeBytes());
        d.setCategory(e.getCategory());
        d.setUploadedBy(e.getUploadedBy());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }
}
