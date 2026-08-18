package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Audit trail for customer profile changes. Every create / update /
 * activate / deactivate / delete is recorded so the Notes & Activity
 * tab can render a full timeline.
 */
@Entity
@Table(name = "customer_audit")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAudit extends ShopAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** Action that triggered this audit entry. */
    @Column(name = "action", nullable = false, length = 30)
    private String action;  // CREATED, UPDATED, ACTIVATED, DEACTIVATED, ARCHIVED, DELETED

    /** Who performed the action (user ID or "system"). */
    @Column(name = "performed_by", length = 100)
    private String performedBy;

    /** JSON-encoded snapshot of changed fields (old→new). */
    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes;

    /** Optional human-readable summary. */
    @Column(name = "summary", length = 500)
    private String summary;

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getChanges() { return changes; }
    public void setChanges(String changes) { this.changes = changes; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
