package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.StatutoryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StatutoryConfigRepository extends JpaRepository<StatutoryConfig, Long> {
    Optional<StatutoryConfig> findByShopId(Long shopId);
}
