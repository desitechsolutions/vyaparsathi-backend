package com.desitech.vyaparsathi.changelog.dto;

import lombok.Data;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;
import java.time.LocalDateTime;

@Data
public class ChangeLogDto {
    private Long id;
    private String entityType;
    private Long entityId;
    private ChangeLogOperation operation;
    private String payloadJson;
    private String deviceId;
    private Long seqNo;
    private LocalDateTime createdAt;
}