package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.TemperatureLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TemperatureLogRepository extends JpaRepository<TemperatureLog, Long> {
    List<TemperatureLog> findByReceivingIdOrderByReadingAtDesc(Long receivingId);
}
