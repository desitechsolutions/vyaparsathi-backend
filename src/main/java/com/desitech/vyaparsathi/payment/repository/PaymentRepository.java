package com.desitech.vyaparsathi.payment.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.payment.entity.Payment;
import com.desitech.vyaparsathi.payment.enums.PaymentSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends BaseRepository<Payment, Long> {
    List<Payment> findBySourceTypeAndSourceId(PaymentSourceType sourceType, Long sourceId);
    List<Payment> findBySupplierId(Long supplierId);
    List<Payment> findByCustomerId(Long customerId);
}