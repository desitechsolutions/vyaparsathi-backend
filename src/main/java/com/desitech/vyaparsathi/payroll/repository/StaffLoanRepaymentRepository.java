package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.StaffLoanRepayment;
import com.desitech.vyaparsathi.payroll.enums.RepaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StaffLoanRepaymentRepository extends JpaRepository<StaffLoanRepayment, Long> {

    List<StaffLoanRepayment> findByLoanIdOrderByInstallmentNumber(Long loanId);

    Optional<StaffLoanRepayment> findByLoanIdAndInstallmentNumber(Long loanId, Integer installmentNumber);

    List<StaffLoanRepayment> findByLoanIdAndPaymentStatus(Long loanId, RepaymentStatus status);

    List<StaffLoanRepayment> findByPaymentStatusAndDueDateBefore(RepaymentStatus status, LocalDate date);

    List<StaffLoanRepayment> findByPayrollSlipId(Long payrollSlipId);

    long countByLoanIdAndPaymentStatus(Long loanId, RepaymentStatus status);
}
