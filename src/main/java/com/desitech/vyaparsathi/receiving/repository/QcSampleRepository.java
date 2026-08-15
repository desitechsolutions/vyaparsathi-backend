package com.desitech.vyaparsathi.receiving.repository;

import com.desitech.vyaparsathi.receiving.entity.QcSample;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QcSampleRepository extends JpaRepository<QcSample, Long> {
    List<QcSample> findByReceivingId(Long receivingId);
}
