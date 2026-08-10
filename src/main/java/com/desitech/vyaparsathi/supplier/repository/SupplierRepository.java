package com.desitech.vyaparsathi.supplier.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;

import java.util.List;

public interface SupplierRepository extends BaseRepository<Supplier, Long> {

    /** Fetch all suppliers belonging to a specific shop. Used for payables aging report. */
    List<Supplier> findByShopId(Long shopId);

    default List<Supplier> findAllByShopId(Long shopId) {
        return findByShopId(shopId);
    }
}
