package com.desitech.vyaparsathi.customer.controller;

import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.customer.dto.CustomerAttachmentDto;
import com.desitech.vyaparsathi.customer.entity.CustomerAttachment;
import com.desitech.vyaparsathi.customer.service.CustomerAttachmentService;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/customers/{customerId}/attachments")
public class CustomerAttachmentController {

    private final CustomerAttachmentService service;

    public CustomerAttachmentController(CustomerAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("CUSTOMER_VIEW")
    public List<CustomerAttachmentDto> list(@PathVariable Long customerId) {
        return service.listByCustomer(customerId);
    }

    @PostMapping
    @RequirePermission("CUSTOMER_EDIT")
    public CustomerAttachmentDto upload(
            @PathVariable Long customerId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category) {
        return service.upload(customerId, file, category);
    }

    @GetMapping("/{attachmentId}/download")
    @RequirePermission("CUSTOMER_VIEW")
    public ResponseEntity<Resource> download(
            @PathVariable Long customerId,
            @PathVariable Long attachmentId) {
        CustomerAttachment meta = service.findById(attachmentId);
        Path path = service.resolveDownloadPath(attachmentId);
        if (!Files.exists(path)) {
            throw new ApplicationException("File is no longer available on disk. It may have been removed.");
        }
        Resource resource = new FileSystemResource(path);
        String contentType = meta.getMimeType() != null ? meta.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + (meta.getFileName() != null ? meta.getFileName() : "file") + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @DeleteMapping("/{attachmentId}")
    @RequirePermission("CUSTOMER_EDIT")
    public void delete(@PathVariable Long customerId, @PathVariable Long attachmentId) {
        service.delete(attachmentId);
    }
}
