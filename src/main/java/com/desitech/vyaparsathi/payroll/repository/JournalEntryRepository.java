package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.JournalEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {

    Optional<JournalEntryEntity> findByPayrollRunId(Long payrollRunId);

    List<JournalEntryEntity> findByShopIdOrderByTransactionDateDesc(Long shopId);

    boolean existsByPayrollRunId(Long payrollRunId);
}
