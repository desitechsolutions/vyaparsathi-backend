package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    // Fetch active staff for a specific shop with pagination
    Page<Staff> findByShopIdAndActiveTrue(Long shopId, Pageable pageable);

    // Fetch all active staff (useful for dropdowns/bulk selection)
    List<Staff> findByShopIdAndActiveTrue(Long shopId);

    // Ensure we don't fetch a deleted staff member by mistake
    Optional<Staff> findByIdAndActiveTrue(Long id);

    // For your bulk selection logic: find all staff who haven't been paid for a specific month
    // (This query is high-performance for MySQL 9.x)
    @Query("SELECT s FROM Staff s WHERE s.shop.id = :shopId AND s.active = true " +
            "AND s.id NOT IN (SELECT p.staff.id FROM PayrollRecord p " +
            "WHERE p.salaryMonth = :month AND p.salaryYear = :year AND p.shop.id = :shopId)")
    List<Staff> findUnpaidStaffForPeriod(Long shopId, String month, Integer year);
}