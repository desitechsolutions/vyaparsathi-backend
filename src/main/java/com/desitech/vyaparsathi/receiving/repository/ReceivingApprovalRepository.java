package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.ReceivingApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReceivingApprovalRepository extends JpaRepository<ReceivingApproval, Long> {
    List<ReceivingApproval> findByReceivingIdOrderByLevelAsc(Long receivingId);
}
