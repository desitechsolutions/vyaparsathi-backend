package com.desitech.vyaparsathi.audit.repository;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends BaseRepository<AuditLog, Long> {
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);
}
