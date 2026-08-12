package com.desitech.vyaparsathi.delivery.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.delivery.entity.DeliveryStatusHistory;

import java.util.List;

public interface DeliveryStatusHistoryRepository extends BaseRepository<DeliveryStatusHistory, Long> {
    List<DeliveryStatusHistory> findByDelivery_IdOrderByChangedAtAsc(Long deliveryId);
}
