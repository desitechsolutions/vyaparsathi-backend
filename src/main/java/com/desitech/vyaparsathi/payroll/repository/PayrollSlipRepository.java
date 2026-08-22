package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PayrollSlip;
import com.desitech.vyaparsathi.payroll.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollSlipRepository extends JpaRepository<PayrollSlip, Long> {
    Page<PayrollSlip> findByPayrollRunId(Long runId, Pageable pageable);

    Optional<PayrollSlip> findByPayrollRunIdAndEmployeeId(Long runId, Long employeeId);

    Page<PayrollSlip> findByEmployeeIdAndShopId(Long employeeId, Long shopId, Pageable pageable);

    Page<PayrollSlip> findByShopIdAndPayoutStatus(Long shopId, PayoutStatus payoutStatus, Pageable pageable);

    List<PayrollSlip> findByPayrollRunIdAndPayoutStatus(Long runId, PayoutStatus payoutStatus);

    long countByPayrollRunIdAndPayoutStatus(Long runId, PayoutStatus payoutStatus);

    List<PayrollSlip> findByCreatedAtBetweenAndShopId(LocalDateTime startDate, LocalDateTime endDate, Long shopId);
}
