package com.desitech.vyaparsathi.changelog.service;

import com.desitech.vyaparsathi.changelog.dto.ChangeLogDto;
import com.desitech.vyaparsathi.changelog.entity.ChangeLog;
import com.desitech.vyaparsathi.changelog.mapper.ChangeLogMapper;
import com.desitech.vyaparsathi.changelog.repository.ChangeLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.List;

@Service
public class ChangeLogService {
    private static final Logger logger = LoggerFactory.getLogger(ChangeLogService.class);

    @Autowired
    private ChangeLogRepository repository;

    @Autowired
    private ChangeLogMapper mapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(String entityType, Long entityId, ChangeLogOperation operation, Object payload, String deviceId) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize changelog payload: {}", e.getMessage(), e);
            payloadJson = "{}";
        }

        ChangeLog log = new ChangeLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOperation(operation);
        log.setPayloadJson(payloadJson);
        log.setDeviceId(deviceId);

        // Use a synchronized block to prevent race conditions on sequence number generation
        synchronized (this) {
            Long maxSeqNo = repository.findMaxSeqNo();
            log.setSeqNo(maxSeqNo == null ? 1L : maxSeqNo + 1);
        }

        repository.save(log);
    }

    public List<ChangeLogDto> getChangeLogsForEntity(String entityType, Long entityId) {
        if (entityType == null || entityId == null) {
            return Collections.emptyList();
        }
        List<ChangeLog> logs = repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
        return mapper.toDtoList(logs);
    }

    public Page<ChangeLogDto> getChangeLogsForEntity(String entityType, Long entityId, ChangeLogOperation operation, Pageable pageable) {
        if (entityType == null || entityId == null) {
            return Page.empty();
        }
        Page<ChangeLog> logs;
        if (operation != null) {
            logs = repository.findByEntityTypeAndEntityIdAndOperationOrderByCreatedAtDesc(entityType, entityId, operation, pageable);
        } else {
            logs = repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable);
        }
        return logs.map(mapper::toDto);
    }
}