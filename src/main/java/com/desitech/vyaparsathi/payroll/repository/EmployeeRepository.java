package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.Employee;
import com.desitech.vyaparsathi.payroll.enums.EmploymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Page<Employee> findByShopIdAndIsActiveTrue(Long shopId, Pageable pageable);

    Page<Employee> findByShopIdAndEmploymentStatus(Long shopId, EmploymentStatus status, Pageable pageable);

    List<Employee> findByShopId(Long shopId);

    List<Employee> findByShopIdAndIsActiveTrueAndEmploymentStatus(Long shopId, EmploymentStatus status);

    Optional<Employee> findByShopIdAndEmployeeCode(Long shopId, String employeeCode);

    Optional<Employee> findByIdAndIsActiveTrue(Long id);

    Optional<Employee> findByShopIdAndPanNumber(Long shopId, String panNumber);

    List<Employee> findBySalaryStructureId(Long structureId);

    long countByShopIdAndEmploymentStatus(Long shopId, EmploymentStatus status);
}
