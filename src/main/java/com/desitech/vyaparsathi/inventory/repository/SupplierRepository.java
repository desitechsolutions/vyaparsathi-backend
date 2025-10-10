package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.inventory.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplierRepository extends BaseRepository<Supplier, Long> {
	// Add custom fetch if Supplier has relations in future (e.g., @EntityGraph)
}
