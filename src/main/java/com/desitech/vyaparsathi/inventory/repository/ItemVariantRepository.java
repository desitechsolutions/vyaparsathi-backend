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
     * Powers the main item search/filter functionality.
     *
     * <p>{@code attribute1} / {@code attribute2} replaced the legacy
     * {@code fabric} / {@code season} predicates in V76. The parameter names
     * are kept for caller-source compatibility but the underlying JPQL now
     * targets the canonical attribute columns.
     */
    @Query("SELECT iv FROM ItemVariant iv JOIN iv.item i LEFT JOIN i.category c WHERE " +
            "iv.active = true AND i.active = true AND " +
            "(:name IS NULL OR i.name LIKE %:name%) AND " +
            "(:categoryName IS NULL OR c.name LIKE %:categoryName%) AND " +
            "(:color IS NULL OR iv.color LIKE %:color%) AND " +
            "(:size IS NULL OR iv.size LIKE %:size%) AND " +
            "(:design IS NULL OR iv.design LIKE %:design%) AND " +
            "(:sku IS NULL OR iv.sku LIKE %:sku%) AND " +
            "(:attribute1 IS NULL OR i.attribute1 LIKE %:attribute1%) AND " +
            "(:attribute2 IS NULL OR i.attribute2 LIKE %:attribute2%) AND " +
            "(:fit IS NULL OR iv.fit LIKE %:fit%) AND " +
            "(:specifications IS NULL OR i.specifications LIKE %:specifications%)")
    List<ItemVariant> searchVariants(
            @Param("name") String name,
            @Param("categoryName") String categoryName,
            @Param("color") String color,
            @Param("size") String size,
            @Param("design") String design,
            @Param("sku") String sku,
            @Param("attribute1") String attribute1,
            @Param("attribute2") String attribute2,
            @Param("fit") String fit,
            @Param("specifications") String specifications
    );

    /**
     * NEW: Finds all variants that have a low stock threshold configured.
     * This is used by the LowStockAlerts feature.
     */
    List<ItemVariant> findAllByLowStockThresholdIsNotNull();

    /**
     * V79 successor to {@link #findAllByLowStockThresholdIsNotNull()}: pulls
     * every variant that has EITHER the legacy {@code lowStockThreshold} OR
     * the new {@code reorderPoint} set. A shop using the enterprise reorder
     * rules can leave {@code lowStockThreshold} null and only configure the
     * new fields — this query keeps those variants in the alerts pool.
     */
    @Query("SELECT iv FROM ItemVariant iv " +
            "JOIN FETCH iv.item i " +
            "LEFT JOIN FETCH iv.preferredSupplier ps " +
            "WHERE iv.active = true AND " +
            "(iv.lowStockThreshold IS NOT NULL OR iv.reorderPoint IS NOT NULL)")
    List<ItemVariant> findAllForLowStockAlerting();

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