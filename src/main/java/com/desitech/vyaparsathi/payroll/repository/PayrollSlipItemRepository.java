package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PayrollSlipItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollSlipItemRepository extends JpaRepository<PayrollSlipItem, Long> {
    List<PayrollSlipItem> findByPayrollSlipIdOrderBySequence(Long slipId);

    List<PayrollSlipItem> findByPayrollSlipIdAndComponentCode(Long slipId, String componentCode);

    List<PayrollSlipItem> findByComponentCode(String componentCode);
}
