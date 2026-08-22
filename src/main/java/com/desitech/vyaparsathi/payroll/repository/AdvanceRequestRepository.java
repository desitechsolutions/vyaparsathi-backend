package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.AdvanceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdvanceRequestRepository extends JpaRepository<AdvanceRequest, Long> {
    Page<AdvanceRequest> findByEmployeeIdOrderByRequestedAtDesc(Long employeeId, Pageable pageable);

    List<AdvanceRequest> findByStatusAndIsActiveTrue(String status);
}
