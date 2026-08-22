package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    Page<BankTransaction> findByShopIdAndStatus(Long shopId, String status, Pageable pageable);

    List<BankTransaction> findByPayrollRunIdAndStatus(Long runId, String status);

    List<BankTransaction> findByEmployeeIdAndInitiatedAtBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    long countByShopIdAndStatus(Long shopId, String status);
}
