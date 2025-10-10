package com.desitech.vyaparsathi.delivery.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.delivery.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeliveryRepository extends BaseRepository<Delivery, Long> {
    List<Delivery> findBySaleId(Long saleId);
}
