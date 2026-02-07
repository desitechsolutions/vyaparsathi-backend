package com.desitech.vyaparsathi.audit.repository;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends BaseRepository<AuditLog, Long> {

    // 1. Standard Date Range Search (Optimized for Export)
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);

    // 2. Paginated search (Essential for the UI Table)
    Page<AuditLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 3. User-specific search with sorting
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);

    /**
     * Future-proofing for your Bulk Selection / Advance Payment implementation.
     * This allows you to delete old logs in one go to keep the DB size under control.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLog a WHERE a.timestamp < :cutoffDate")
    void deleteLogsOlderThan(LocalDateTime cutoffDate);
}