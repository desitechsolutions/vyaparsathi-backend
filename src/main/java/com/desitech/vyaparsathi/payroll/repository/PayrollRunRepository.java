package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PayrollRun;
import com.desitech.vyaparsathi.payroll.enums.PayrollRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    boolean existsByShopIdAndPayrollMonthAndPayrollYear(Long shopId, String payrollMonth, Integer payrollYear);

    Optional<PayrollRun> findByShopIdAndPayrollMonthAndPayrollYear(Long shopId, String payrollMonth, Integer payrollYear);

    Page<PayrollRun> findByShopIdOrderByPayrollYearDescPayrollMonthDesc(Long shopId, Pageable pageable);

    Page<PayrollRun> findByShopIdAndStatus(Long shopId, PayrollRunStatus status, Pageable pageable);

    List<PayrollRun> findByShopIdAndStatusAndPayrollYearAndPayrollMonth(Long shopId, PayrollRunStatus status, Integer payrollYear, String payrollMonth);

    List<PayrollRun> findByStatusAndCreatedAtBefore(PayrollRunStatus status, LocalDateTime date);

    long countByShopIdAndStatus(Long shopId, PayrollRunStatus status);

    long countByShopIdAndPayrollMonthAndPayrollYear(Long shopId, String payrollMonth, Integer payrollYear);
}
