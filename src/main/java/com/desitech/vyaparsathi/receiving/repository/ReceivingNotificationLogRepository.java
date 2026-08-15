package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.ReceivingNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceivingNotificationLogRepository extends JpaRepository<ReceivingNotificationLog, Long> {
    List<ReceivingNotificationLog> findByReceivingIdOrderBySentAtDesc(Long receivingId);
}
