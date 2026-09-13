package com.desitech.vyaparsathi.gst.repository;

import com.desitech.vyaparsathi.gst.entity.HsnMaster;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface HsnMasterRepository extends JpaRepository<HsnMaster, Long> {

    @Query("SELECT h FROM HsnMaster h WHERE h.hsnCode LIKE :codePfx OR LOWER(h.description) LIKE LOWER(:descLike)")
    List<HsnMaster> search(@Param("codePfx") String codePfx,
                           @Param("descLike") String descLike,
                           Pageable pageable);
}
