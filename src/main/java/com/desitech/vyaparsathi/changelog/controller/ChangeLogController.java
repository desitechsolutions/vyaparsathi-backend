package com.desitech.vyaparsathi.changelog.controller;

import com.desitech.vyaparsathi.changelog.dto.ChangeLogDto;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import com.desitech.vyaparsathi.changelog.service.ChangeLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/changelog")
@PreAuthorize("hasAnyRole('OWNER', 'STAFF')")
public class ChangeLogController {

    @Autowired
    private ChangeLogService service;

    @GetMapping("/{entityType}/{entityId}")
    public ResponseEntity<Page<ChangeLogDto>> getChangeLogs(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(required = false) ChangeLogOperation operation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ChangeLogDto> logs = service.getChangeLogsForEntity(entityType, entityId, operation, pageable);
        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logs);
    }
}