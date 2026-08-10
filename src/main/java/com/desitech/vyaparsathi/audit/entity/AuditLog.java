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

    private String ipAddress;
    private String userAgent;

    @Column(name = "actor_admin_id")
    private Long actorAdminId;

    @Column(name = "target_shop_id")
    private Long targetShopId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "impersonation_session_id")
    private String impersonationSessionId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntity() { return entity; }
    public void setEntity(String entity) { this.entity = entity; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Long getActorAdminId() { return actorAdminId; }
    public void setActorAdminId(Long actorAdminId) { this.actorAdminId = actorAdminId; }

    public Long getTargetShopId() { return targetShopId; }
    public void setTargetShopId(Long targetShopId) { this.targetShopId = targetShopId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getImpersonationSessionId() { return impersonationSessionId; }
    public void setImpersonationSessionId(String impersonationSessionId) { this.impersonationSessionId = impersonationSessionId; }
}
