package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.ReceivingStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceivingStatusHistoryRepository
        extends JpaRepository<ReceivingStatusHistory, Long> {

    List<ReceivingStatusHistory> findByReceivingIdOrderByChangedAtAsc(Long receivingId);
}
