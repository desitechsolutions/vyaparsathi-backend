package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.SalaryComponent;
import com.desitech.vyaparsathi.payroll.enums.ComponentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {

    List<SalaryComponent> findByStructureIdOrderByOrderSequence(Long structureId);

    Optional<SalaryComponent> findByStructureIdAndComponentCode(Long structureId, String code);

    List<SalaryComponent> findByStructureIdAndComponentType(Long structureId, ComponentType type);

    List<SalaryComponent> findByStructureIdAndIsActiveTrue(Long structureId);

    List<SalaryComponent> findByShopIdAndComponentCode(Long shopId, String code);
}
