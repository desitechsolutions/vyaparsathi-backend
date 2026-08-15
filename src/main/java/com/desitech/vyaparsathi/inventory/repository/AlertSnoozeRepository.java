package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.AlertSnooze;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlertSnoozeRepository extends JpaRepository<AlertSnooze, Long> {
    Optional<AlertSnooze> findByUserIdAndAlertTypeAndAlertKey(Long userId, String alertType, String alertKey);
    List<AlertSnooze> findByUserIdAndAlertType(Long userId, String alertType);
}
