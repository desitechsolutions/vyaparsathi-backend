package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.TdsSlab;
import com.desitech.vyaparsathi.payroll.enums.TaxRegime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TdsSlabRepository extends JpaRepository<TdsSlab, Long> {
    @Query("SELECT t FROM TdsSlab t WHERE t.shop.id = ?1 AND t.taxRegime = ?2 AND t.isActive = true AND t.effectiveFrom <= ?3 AND ?4 BETWEEN t.incomeFrom AND t.incomeTo ORDER BY t.effectiveFrom DESC LIMIT 1")
    Optional<TdsSlab> findSlabForIncome(Long shopId, TaxRegime regime, BigDecimal income, LocalDate date);
}
