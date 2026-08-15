package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.ReceivingBinAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceivingBinAssignmentRepository extends JpaRepository<ReceivingBinAssignment, Long> {
    List<ReceivingBinAssignment> findByReceivingItemId(Long receivingItemId);
}
