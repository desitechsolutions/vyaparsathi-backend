package com.desitech.vyaparsathi.delivery.repository;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.delivery.entity.DeliveryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryStatusHistoryRepository extends BaseRepository<DeliveryStatusHistory, Long> {
    List<DeliveryStatusHistory> findByDelivery_Id(Long deliveryId);
}