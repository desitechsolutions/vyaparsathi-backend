package com.desitech.vyaparsathi.inventory.repository;

import com.desitech.vyaparsathi.inventory.entity.ProductBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {
    Optional<ProductBundle> findByBundleVariantId(Long bundleVariantId);
    List<ProductBundle> findByActiveTrue();
}
