package com.desitech.vyaparsathi.gst.repository;

import com.desitech.vyaparsathi.gst.entity.Gstr2bEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface Gstr2bEntryRepository extends JpaRepository<Gstr2bEntry, Long> {

    List<Gstr2bEntry> findByGstr2bImportId(Long importId);

    List<Gstr2bEntry> findByGstr2bImportIdAndMatchStatus(Long importId, String matchStatus);
}
