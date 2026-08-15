package com.desitech.vyaparsathi.einvoice.repository;

import com.desitech.vyaparsathi.einvoice.entity.EInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EInvoiceRepository extends JpaRepository<EInvoice, Long> {
    Optional<EInvoice> findFirstByDocumentTypeAndDocumentIdAndStatus(String documentType, Long documentId, String status);
    Optional<EInvoice> findByIrn(String irn);
}
