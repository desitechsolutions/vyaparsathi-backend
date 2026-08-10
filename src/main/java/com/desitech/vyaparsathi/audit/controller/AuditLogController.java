package com.desitech.vyaparsathi.audit.controller;

import com.desitech.vyaparsathi.audit.dto.AuditLogDto;
import com.desitech.vyaparsathi.audit.export.AuditLogExportService;
import com.desitech.vyaparsathi.audit.service.AuditLogService;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.common.exception.ExportAppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF', 'ADMIN', 'SUPER_ADMIN', 'TECH_ADMIN', 'SUPPORT_AGENT')")
@RequiredArgsConstructor // Automatically injects final fields (Cleaner than @Autowired)
@Tag(name = "Audit Log", description = "Operations for viewing and searching security audit trails")
public class AuditLogController {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogController.class);

    private final AuditLogService service;
    private final AuditLogExportService exportService;

    @GetMapping
    @Operation(summary = "Get audit logs (Paginated)", description = "Retrieve logs with pagination. Defaults to last 24 hours.")
    public ResponseEntity<Page<AuditLogDto>> getLogs(
            @Parameter(description = "Start date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        try {
            LocalDateTime start = (startDate == null) ? LocalDateTime.now().minusDays(1) : startDate;
            LocalDateTime end = (endDate == null) ? LocalDateTime.now() : endDate;

            // Notice we use getLogsPaginated here!
            Page<AuditLogDto> result = service.getLogsPaginated(start, end, pageable);

            logger.info("Fetched page {} of audit logs", pageable.getPageNumber());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching audit logs: {}", e.getMessage());
            throw new ApplicationException("Failed to fetch audit logs", e);
        }
    }
    @GetMapping("/user/{username}")
    @Operation(summary = "Get audit logs for user", description = "Retrieve all audit logs for a specific username")
    public ResponseEntity<List<AuditLogDto>> getLogsByUser(@PathVariable String username) {
        try {
            List<AuditLogDto> result = service.getLogsByUser(username);
            logger.info("Fetched {} audit logs for user={}", result.size(), username);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching audit logs for user={}: {}", username, e.getMessage());
            throw new ApplicationException("Failed to fetch audit logs for user", e);
        }
    }

    @GetMapping("/export")
    @Operation(summary = "Export audit logs", description = "Export logs as CSV, Excel, or PDF")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "csv") String format) {
        try {
            List<AuditLogDto> data = service.getLogs(startDate, endDate);
            byte[] fileContent = exportService.exportAuditLogs(data, format);

            String fileName = String.format("audit-logs-%s-to-%s.%s",
                    startDate.toLocalDate(),
                    endDate.toLocalDate(),
                    getFileExtension(format));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(getMediaType(format))
                    .body(fileContent);
        } catch (ExportAppException e) {
            logger.error("Failed to export audit logs: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error during audit log export", e);
            throw new ApplicationException("Failed to export audit logs", e);
        }
    }

    /**
     * Helper to determine Media Type for export
     */
    private MediaType getMediaType(String format) {
        return switch (format.toLowerCase()) {
            case "csv" -> MediaType.parseMediaType("text/csv");
            case "excel" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /**
     * Helper to determine File Extension for export
     */
    private String getFileExtension(String format) {
        return switch (format.toLowerCase()) {
            case "excel" -> "xlsx";
            case "pdf" -> "pdf";
            default -> "csv";
        };
    }
}