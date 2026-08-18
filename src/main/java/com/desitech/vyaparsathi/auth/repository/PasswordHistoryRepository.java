package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.PasswordHistory;
import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@SkipShopFilter
@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

    List<PasswordHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PasswordHistory p WHERE p.userId = :userId AND p.id NOT IN (" +
            "SELECT p2.id FROM PasswordHistory p2 WHERE p2.userId = :userId " +
            "ORDER BY p2.createdAt DESC LIMIT :window)")
    int trimHistoryBeyondWindow(@Param("userId") Long userId, @Param("window") int window);
}
