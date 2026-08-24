package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PayslipDispatchLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayslipDispatchLogRepository extends JpaRepository<PayslipDispatchLog, Long> {
    List<PayslipDispatchLog> findByPayrollSlipId(Long slipId);

    Page<PayslipDispatchLog> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    List<PayslipDispatchLog> findByDeliveryStatus(String status);

    // Aggregation queries for getDispatchStats (eliminates N+1)
    @Query("SELECT COUNT(d) FROM PayslipDispatchLog d " +
           "WHERE d.payrollSlip.payrollRun.id = :runId " +
           "AND d.dispatchMethod = :method AND d.deliveryStatus = :status")
    long countByPayrollRunIdAndMethodAndStatus(Long runId, String method, String status);

    @Query("SELECT COUNT(d) FROM PayslipDispatchLog d " +
           "WHERE d.payrollSlip.payrollRun.id = :runId AND d.deliveryStatus = :status")
    long countByPayrollRunIdAndStatus(Long runId, String status);
}
