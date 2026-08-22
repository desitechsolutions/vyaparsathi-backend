package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PtSlab;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface PtSlabRepository extends JpaRepository<PtSlab, Long> {
    @Query("SELECT p FROM PtSlab p WHERE p.shop.id = ?1 AND p.ptState = ?2 AND p.isActive = true AND p.effectiveFrom <= ?3 AND ?4 BETWEEN p.salaryFrom AND p.salaryTo ORDER BY p.effectiveFrom DESC LIMIT 1")
    Optional<PtSlab> findSlabForSalary(Long shopId, String ptState, BigDecimal salary, LocalDate date);
}
