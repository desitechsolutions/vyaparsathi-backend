package com.desitech.vyaparsathi.payroll.repository;

import com.desitech.vyaparsathi.payroll.entity.EssPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EssPreferencesRepository extends JpaRepository<EssPreferences, Long> {
    Optional<EssPreferences> findByEmployeeId(Long employeeId);
}
