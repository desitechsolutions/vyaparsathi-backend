package com.desitech.vyaparsathi.document.repository;

import com.desitech.vyaparsathi.common.enums.DocumentType;
import com.desitech.vyaparsathi.document.entity.DocumentPrintAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentPrintAuditRepository extends JpaRepository<DocumentPrintAudit, Long> {

    long countByDocumentTypeAndDocumentId(DocumentType documentType, Long documentId);
}
