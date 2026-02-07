package com.desitech.vyaparsathi.supplier.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.supplier.entity.Supplier;

public interface SupplierRepository extends BaseRepository<Supplier, Long> {
	// Add custom fetch if Supplier has relations in future (e.g., @EntityGraph)
}
