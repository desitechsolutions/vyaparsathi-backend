package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.LeaveApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {
    Page<LeaveApplication> findByEmployeeIdAndStatus(Long employeeId, String status, Pageable pageable);

    Page<LeaveApplication> findByShopIdAndStatus(Long shopId, String status, Pageable pageable);

    List<LeaveApplication> findByEmployeeIdAndFromDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    List<LeaveApplication> findByShopIdAndFromDateBetween(Long shopId, LocalDate startDate, LocalDate endDate);

    long countByEmployeeIdAndStatus(Long employeeId, String status);

    Page<LeaveApplication> findByEmployeeId(Long employeeId, Pageable pageable);

    List<LeaveApplication> findByShopIdAndStatusOrderByIdDesc(Long shopId, String status);
}
