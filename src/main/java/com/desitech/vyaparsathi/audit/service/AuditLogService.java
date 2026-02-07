package com.desitech.vyaparsathi.audit.service;

import com.desitech.vyaparsathi.audit.dto.AuditLogDto;
import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;

    /**
     * Records a new audit entry. Used by AuditAspect.
     */
    @Transactional
    public void log(String username, String action, String entity, String entityId, String details, String ip, String ua) {
        AuditLog log = new AuditLog();
        log.setUsername(username);
        log.setAction(action);
        log.setEntity(entity);
        log.setEntityId(entityId);
        log.setDetails(details);
        log.setIpAddress(ip);
        log.setUserAgent(ua);
        log.setTimestamp(LocalDateTime.now());

        repository.save(log);
    }

    /**
     * Paginated retrieval for the UI table to ensure high performance.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDto> getLogsPaginated(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return repository.findByTimestampBetween(start, end, pageable)
                .map(this::toDto);
    }

    /**
     * List retrieval for Exports (CSV/Excel/PDF) where we need the full dataset.
     */
    @Transactional(readOnly = true)
    public List<AuditLogDto> getLogs(LocalDateTime start, LocalDateTime end) {
        return repository.findByTimestampBetweenOrderByTimestampDesc(start, end)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Filter logs by a specific user.
     */
    @Transactional(readOnly = true)
    public List<AuditLogDto> getLogsByUser(String username) {
        return repository.findByUsernameOrderByTimestampDesc(username)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Mapper to convert Entity to DTO, including forensic IP and User Agent info.
     */
    private AuditLogDto toDto(AuditLog log) {
        return new AuditLogDto(
                log.getId(),
                log.getUsername(),
                log.getAction(),
                log.getEntity(),
                log.getEntityId(),
                log.getDetails(),
                log.getTimestamp(),
                log.getIpAddress(),
                log.getUserAgent()
        );
    }
}