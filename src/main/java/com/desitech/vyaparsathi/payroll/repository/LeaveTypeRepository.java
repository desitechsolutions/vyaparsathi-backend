package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.LeaveType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Page<LeaveType> findByShopIdAndIsActive(Long shopId, Boolean isActive, Pageable pageable);

    List<LeaveType> findByShopIdAndIsActiveTrue(Long shopId);

    Optional<LeaveType> findByShopIdAndName(Long shopId, String name);

    long countByShopIdAndIsActive(Long shopId, Boolean isActive);
}
