package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, Integer year);

    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, Integer year);

    List<LeaveBalance> findByShopIdAndYear(Long shopId, Integer year);

    List<LeaveBalance> findByEmployeeId(Long employeeId);

    long countByEmployeeIdAndYear(Long employeeId, Integer year);
}
