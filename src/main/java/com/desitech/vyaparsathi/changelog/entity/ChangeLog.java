
package com.desitech.vyaparsathi.changelog.entity;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "change_log")
@Data
@NoArgsConstructor
public class ChangeLog extends ShopAwareEntity {

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeLogOperation operation;

    @Lob
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "seq_no", nullable = false)
    private Long seqNo;
}