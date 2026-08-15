package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.UomConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UomConversionRepository extends JpaRepository<UomConversion, Long> {
    Optional<UomConversion> findByFromUnitAndToUnitAndActiveTrue(String fromUnit, String toUnit);
    List<UomConversion> findByActiveTrue();
}
