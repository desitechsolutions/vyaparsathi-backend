package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.StaffLoan;
import com.desitech.vyaparsathi.payroll.enums.LoanStatus;
import com.desitech.vyaparsathi.payroll.enums.LoanType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffLoanRepository extends JpaRepository<StaffLoan, Long> {

    List<StaffLoan> findByEmployeeId(Long employeeId);

    List<StaffLoan> findByEmployeeIdAndStatus(Long employeeId, LoanStatus status);

    Page<StaffLoan> findByShopIdAndStatus(Long shopId, LoanStatus status, Pageable pageable);

    List<StaffLoan> findByShopIdAndLoanType(Long shopId, LoanType type);

    Optional<StaffLoan> findByShopIdAndLoanNumber(Long shopId, String loanNumber);

    List<StaffLoan> findByEmployeeIdAndStatusOrderByDisbursementDateDesc(Long employeeId, LoanStatus status);

    long countByEmployeeIdAndStatus(Long employeeId, LoanStatus status);
}
