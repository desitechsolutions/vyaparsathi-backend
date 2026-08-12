package com.desitech.vyaparsathi.purchases.repository;

import com.desitech.vyaparsathi.purchases.entity.PurchaseInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface PurchaseInvoiceItemRepository extends JpaRepository<PurchaseInvoiceItem, Long> {

    /**
     * Cost history for a batch of variants, most-recent first (by parent invoice's
     * purchase date, then row id as a tiebreaker for same-day invoices).
     *
     * Callers typically reduce this to "latest cost per variant" by walking the list
     * once and keeping the first row seen per variant — this avoids N+1 queries when
     * computing margin over many sale lines.
     *
     * Rows: {@code [variantId, unitCost]}.
     */
    @Query("SELECT pii.itemVariant.id, pii.unitCost " +
            "FROM PurchaseInvoiceItem pii " +
            "WHERE pii.itemVariant.id IN :variantIds " +
            "ORDER BY pii.purchaseInvoice.purchaseDate DESC, pii.id DESC")
    List<Object[]> findLatestCostsByVariantIds(@Param("variantIds") Set<Long> variantIds);
}
