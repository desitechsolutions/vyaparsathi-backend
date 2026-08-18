package com.desitech.vyaparsathi.customer.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * File pointer for a customer-scoped attachment (KYC docs, MSME
 * certificate, master service agreement, one-off invoice PDF from
 * the customer, etc.). Storage lives in {@code FileStorageService};
 * this row just carries the reference + display metadata for the UI.
 */
@Entity
@Table(name = "customer_attachment")
@Getter
@Setter
@NoArgsConstructor
public class CustomerAttachment extends ShopAwareEntity {

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /** KYC · CONTRACT · PO · OTHER — free-text so shops can extend. */
    @Column(length = 40)
    private String category;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;
}
