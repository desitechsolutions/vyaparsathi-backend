package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedViewRepository extends JpaRepository<SavedView, Long> {
    List<SavedView> findByUserIdAndSurface(Long userId, String surface);
}
