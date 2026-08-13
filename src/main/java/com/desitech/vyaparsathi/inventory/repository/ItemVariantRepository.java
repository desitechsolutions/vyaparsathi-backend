package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.entity.ItemVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ItemVariantRepository extends BaseRepository<ItemVariant, Long> {

    Optional<ItemVariant> findBySku(String sku);

    /** POS barcode / QR scanner lookup — finds the variant matching the scanned barcode. */
    Optional<ItemVariant> findByBarcode(String barcode);

    boolean existsByHsn(String hsn);

    /**
     * Searches for ItemVariants based on a combination of item and variant attributes.
     * This query is designed to power the main item search/filter functionality.
     */
    @Query("SELECT iv FROM ItemVariant iv JOIN iv.item i LEFT JOIN i.category c WHERE " +
            "(:name IS NULL OR i.name LIKE %:name%) AND " +
            "(:categoryName IS NULL OR c.name LIKE %:categoryName%) AND " +
            "(:color IS NULL OR iv.color LIKE %:color%) AND " +
            "(:size IS NULL OR iv.size LIKE %:size%) AND " +
            "(:design IS NULL OR iv.design LIKE %:design%) AND " +
            "(:sku IS NULL OR iv.sku LIKE %:sku%) AND " +
            "(:fabric IS NULL OR i.fabric LIKE %:fabric%) AND " +
            "(:season IS NULL OR i.season LIKE %:season%) AND " +
            "(:fit IS NULL OR iv.fit LIKE %:fit%) AND " +
            "(:specifications IS NULL OR i.specifications LIKE %:specifications%)")
    List<ItemVariant> searchVariants(
            @Param("name") String name,
            @Param("categoryName") String categoryName,
            @Param("color") String color,
            @Param("size") String size,
            @Param("design") String design,
            @Param("sku") String sku,
            @Param("fabric") String fabric,
            @Param("season") String season,
            @Param("fit") String fit,
            @Param("specifications") String specifications
    );

    /**
     * NEW: Finds all variants that have a low stock threshold configured.
     * This is used by the LowStockAlerts feature.
     */
    List<ItemVariant> findAllByLowStockThresholdIsNotNull();

    /**
     * Finds all ItemVariants belonging to a specific shop.
     * Used for shop-scoped analytics queries.
     */
    @Query("SELECT iv FROM ItemVariant iv JOIN iv.item i WHERE i.shop.id = :shopId")
    List<ItemVariant> findAllByShopId(@Param("shopId") Long shopId);

    /**
     * Finds all variants of items sharing the same specifications as the given item.
     * Used to suggest equivalent substitutes (same key attributes, different SKU).
     *
     * @param specifications the product specifications / key-attributes string
     * @param excludeItemId the item ID to exclude (the reference item itself)
     * @return list of variants from items with the same specifications
     */
    @Query("SELECT iv FROM ItemVariant iv JOIN iv.item i WHERE i.specifications = :specifications AND i.id <> :excludeItemId")
    List<ItemVariant> findSubstitutesByComposition(
            @Param("specifications") String specifications,
            @Param("excludeItemId") Long excludeItemId
    );

    /**
     * Finds all variants with an expiry date set and expiry date before or on the given cutoff date.
     * Used to generate expiry alerts for perishables (FMCG, food, cosmetics).
     *
     * @param cutoffDate the date up to which items are considered near-expiry or expired
     * @return list of item variants expiring at or before cutoffDate
     */
    @Query("SELECT iv FROM ItemVariant iv WHERE iv.expiryDate IS NOT NULL AND iv.expiryDate <= :cutoffDate")
    List<ItemVariant> findByExpiryDateOnOrBefore(@Param("cutoffDate") LocalDate cutoffDate);

}