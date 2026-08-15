package com.desitech.vyaparsathi.einvoice.repository;

import com.desitech.vyaparsathi.einvoice.entity.EWayBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EWayBillRepository extends JpaRepository<EWayBill, Long> {
    Optional<EWayBill> findFirstByDocumentTypeAndDocumentIdAndStatus(String documentType, Long documentId, String status);
    Optional<EWayBill> findByEwbNumber(String ewbNumber);
}
