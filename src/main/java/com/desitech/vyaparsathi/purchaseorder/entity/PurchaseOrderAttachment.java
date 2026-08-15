package com.desitech.vyaparsathi.purchaseorder.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * File attached to a {@link PurchaseOrder} — quotes, price lists, spec sheets
 * uploaded by the shop for reference or forwarded to the supplier. Mirrors the
 * {@code ReceivingTicketAttachment} pattern: metadata rows persist here,
 * bytes live in the configured storage backend and are addressed by
 * {@code filePath}.
 */
@Entity
@Table(name = "purchase_order_attachment")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderAttachment extends ShopAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "uploaded_by")
    private Long uploadedBy;
}