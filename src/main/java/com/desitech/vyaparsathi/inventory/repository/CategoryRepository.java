package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends BaseRepository<Category, Long> {

    // Find all top-level categories (those with no parent)
    List<Category> findByParentIdIsNull();

    // Find all direct children of a given category
    List<Category> findByParentId(Long parentId);

    // Useful for finding a specific category by name under a parent
    Optional<Category> findByNameAndParentId(String name, Long parentId);

    boolean existsByNameAndShopId(String name, Long shopId);
    Optional<Category> findByNameAndShopId(String name, Long shopId);

}