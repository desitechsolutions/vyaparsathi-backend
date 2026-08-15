package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.ApInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApInvoiceRepository extends JpaRepository<ApInvoice, Long> {
    Optional<ApInvoice> findByReceivingId(Long receivingId);
    List<ApInvoice> findBySupplierIdOrderByInvoiceDateDesc(Long supplierId);
    List<ApInvoice> findByMatchStatus(String matchStatus);
}
