package com.desitech.vyaparsathi.expense.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.expense.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends BaseRepository<Expense, Long> {

    @Query("SELECT e FROM Expense e WHERE e.deleted = false AND e.shop.id = :shopId")
    Page<Expense> findByShopIdAndNotDeleted(@Param("shopId") Long shopId, Pageable pageable);
    Optional<Expense> findByIdAndDeletedFalse(Long id);

    // Optional: Filter by date range for reports
    List<Expense> findByDateBetweenAndDeletedFalse(LocalDateTime start, LocalDateTime end);
}