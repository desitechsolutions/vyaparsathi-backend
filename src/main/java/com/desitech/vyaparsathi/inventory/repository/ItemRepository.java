package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.entity.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends BaseRepository<Item, Long> {

    @EntityGraph(attributePaths = {"variants", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT i FROM Item i WHERE i.active = true")
    List<Item> findAllWithVariants();

    @EntityGraph(attributePaths = {"variants", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT i FROM Item i WHERE i.id = :id AND i.active = true")
    Optional<Item> findByIdWithVariants(Long id);

    /**
     * Checks if any item is associated with the given category ID.
     * This is used to prevent deletion of a category that is in use.
     * @param categoryId The ID of the category to check.
     * @return true if an item exists with that categoryId, false otherwise.
     */
    boolean existsByCategoryId(Long categoryId);

    List<Item> findByNameContainingIgnoreCase(String name);
    boolean existsByNameAndBrandNameAndShopId(String name, String brandName, Long shopId);

    /**
     * Server-side search for the ItemsPage grid. Filters on name / brand
     * substring (case-insensitive) with an optional category filter.
     * Returns a {@link Page} so the caller can pass paging metadata back
     * to a MUI DataGrid running in server mode.
     */
    @Query(
        value = "SELECT i FROM Item i WHERE i.active = true AND " +
                "(:q IS NULL OR :q = '' OR " +
                "  LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(i.brandName, '')) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                "(:categoryId IS NULL OR i.category.id = :categoryId)",
        countQuery = "SELECT COUNT(i) FROM Item i WHERE i.active = true AND " +
                "(:q IS NULL OR :q = '' OR " +
                "  LOWER(i.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
                "  LOWER(COALESCE(i.brandName, '')) LIKE LOWER(CONCAT('%', :q, '%'))) AND " +
                "(:categoryId IS NULL OR i.category.id = :categoryId)"
    )
    Page<Item> searchAll(@Param("q") String q,
                         @Param("categoryId") Long categoryId,
                         Pageable pageable);

    long countByShopId(Long shopId);
}