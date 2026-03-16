package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.entity.Item;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemRepository extends BaseRepository<Item, Long> {

    @EntityGraph(attributePaths = {"variants", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT i FROM Item i")
    List<Item> findAllWithVariants();

    @EntityGraph(attributePaths = {"variants", "category"}, type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
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

}