package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.SupplierRateCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRateCardRepository extends JpaRepository<SupplierRateCard, Long> {
    List<SupplierRateCard> findBySupplierId(Long supplierId);
    List<SupplierRateCard> findByItemVariantId(Long itemVariantId);
    Optional<SupplierRateCard> findFirstBySupplierIdAndItemVariantIdAndValidFromLessThanEqualOrderByValidFromDesc(
            Long supplierId, Long itemVariantId, LocalDate today);
}
