package com.desitech.vyaparsathi.compliance.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.compliance.entity.CompliancePeriodLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@SkipShopFilter
public interface CompliancePeriodLockRepository extends JpaRepository<CompliancePeriodLock, Long> {

    List<CompliancePeriodLock> findByShopIdAndPeriodYearOrderByPeriodMonth(
            @Param("shopId") Long shopId,
            @Param("periodYear") int periodYear);

    Optional<CompliancePeriodLock> findByShopIdAndPeriodYearAndPeriodMonthAndFormType(
            @Param("shopId") Long shopId,
            @Param("periodYear") int periodYear,
            @Param("periodMonth") int periodMonth,
            @Param("formType") String formType);

    List<CompliancePeriodLock> findByShopIdAndPeriodYearAndPeriodMonthAndStatus(
            @Param("shopId") Long shopId,
            @Param("periodYear") int periodYear,
            @Param("periodMonth") int periodMonth,
            @Param("status") String status);
}
