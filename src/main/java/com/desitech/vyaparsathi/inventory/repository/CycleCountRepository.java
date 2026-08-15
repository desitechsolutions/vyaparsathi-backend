package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.CycleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CycleCountRepository extends JpaRepository<CycleCount, Long> {
    List<CycleCount> findByStatusOrderByCreatedAtDesc(String status);
}
