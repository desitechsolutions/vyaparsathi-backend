package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.Form16Data;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface Form16DataRepository extends JpaRepository<Form16Data, Long> {
    Optional<Form16Data> findByEmployeeIdAndFinancialYear(Long employeeId, String financialYear);
}
