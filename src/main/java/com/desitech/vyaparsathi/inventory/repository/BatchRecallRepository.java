package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.BatchRecall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BatchRecallRepository extends JpaRepository<BatchRecall, Long> {
    List<BatchRecall> findByBatchNumber(String batchNumber);
    List<BatchRecall> findByStatus(String status);
}
