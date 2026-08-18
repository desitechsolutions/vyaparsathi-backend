package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User-written note attached to a customer. Explicitly distinct from
 * {@link CustomerAudit} — audit records system events (profile
 * updated, activated), notes record human intent ("negotiated NET-45
 * for Q3", "phone number changed after wedding").
 *
 * <p>{@code pinned} lets the UI float important notes to the top of
 * the customer detail Notes tab regardless of recency.</p>
 */
@Entity
@Table(name = "customer_note")
@Getter
@Setter
@NoArgsConstructor
public class CustomerNote extends ShopAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "author_user_id")
    private Long authorUserId;

    /** Denormalised author display name — survives user deletion. */
    @Column(name = "author_name", length = 120)
    private String authorName;

    @Column(nullable = false)
    private Boolean pinned = Boolean.FALSE;

    public Boolean getPinned() { return pinned != null && pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned != null && pinned; }
}
