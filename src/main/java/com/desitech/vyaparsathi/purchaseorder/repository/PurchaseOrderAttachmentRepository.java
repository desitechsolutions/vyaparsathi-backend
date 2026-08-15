package com.desitech.vyaparsathi.purchaseorder.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.purchaseorder.entity.PurchaseOrderAttachment;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderAttachmentRepository extends BaseRepository<PurchaseOrderAttachment, Long> {

    /**
     * List all attachments for a given PO, ordered newest first. Sorting by
     * {@code createdAt} DESC comes from {@link com.desitech.vyaparsathi.common.entities.BaseEntity};
     * derived-query naming picks up the sort from the method signature.
     */
    List<PurchaseOrderAttachment> findByPurchaseOrderIdOrderByCreatedAtDesc(Long purchaseOrderId);
}