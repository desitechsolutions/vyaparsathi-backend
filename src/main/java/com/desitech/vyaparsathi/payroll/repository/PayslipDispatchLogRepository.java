package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PayslipDispatchLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayslipDispatchLogRepository extends JpaRepository<PayslipDispatchLog, Long> {
    List<PayslipDispatchLog> findByPayrollSlipId(Long slipId);

    Page<PayslipDispatchLog> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId, Pageable pageable);

    List<PayslipDispatchLog> findByDeliveryStatus(String status);
}
