package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.PasswordHistory;
import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@SkipShopFilter
@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
