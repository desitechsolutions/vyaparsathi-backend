package com.desitech.vyaparsathi.gst.repository;

import com.desitech.vyaparsathi.gst.entity.Gstr2bImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface Gstr2bImportRepository extends JpaRepository<Gstr2bImport, Long> {

    Optional<Gstr2bImport> findTopByShopIdAndReturnPeriodOrderByImportedAtDesc(
            Long shopId, String returnPeriod);
}
