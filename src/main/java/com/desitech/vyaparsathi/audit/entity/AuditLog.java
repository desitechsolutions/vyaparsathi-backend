package com.desitech.vyaparsathi.audit.entity;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.shop.entity.Shop;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog extends ShopAwareEntity {

    private String username;
    private String action;
    private String entity;
    private String entityId;
    @Column(length = 2000)
    private String details;
    private LocalDateTime timestamp;
}
