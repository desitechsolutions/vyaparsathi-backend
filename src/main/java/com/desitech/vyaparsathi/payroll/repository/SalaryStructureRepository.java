package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.SalaryStructure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {

    Page<SalaryStructure> findByShopIdAndIsActiveTrue(Long shopId, Pageable pageable);

    Optional<SalaryStructure> findByShopIdAndStructureCode(Long shopId, String code);

    List<SalaryStructure> findByShopIdAndIsActive(Long shopId, Boolean isActive);

    List<SalaryStructure> findByShopId(Long shopId);
}
