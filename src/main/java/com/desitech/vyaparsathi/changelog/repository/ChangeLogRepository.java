package com.desitech.vyaparsathi.changelog.repository;

import com.desitech.vyaparsathi.changelog.entity.ChangeLog;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.desitech.vyaparsathi.changelog.model.ChangeLogOperation;

public interface ChangeLogRepository extends BaseRepository<ChangeLog, Long> {
    List<ChangeLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    Page<ChangeLog> findByEntityTypeAndEntityIdAndOperationOrderByCreatedAtDesc(String entityType, Long entityId, ChangeLogOperation operation, Pageable pageable);
    Page<ChangeLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId, Pageable pageable);

    @Query("SELECT MAX(c.seqNo) FROM ChangeLog c")
    Long findMaxSeqNo();
}