package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.PayrollRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {

    // List all payments for a specific staff member in a shop
    Page<PayrollRecord> findByShopIdAndStaffId(Long shopId, Long staffId, Pageable pageable);

    // List all payments for the entire shop (Payroll History)
    Page<PayrollRecord> findByShopId(Long shopId, Pageable pageable);

    /**
     * Critical for Production: Prevents duplicate salary entries.
     * Checks if a record already exists for the staff, month, year, and shop.
     */
    boolean existsByStaffIdAndSalaryMonthAndSalaryYearAndShopId(
            Long staffId,
            String salaryMonth,
            Integer salaryYear,
            Long shopId
    );
}