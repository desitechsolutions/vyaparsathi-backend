package com.desitech.vyaparsathi.shop.customfield.repository;

import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.shop.customfield.entity.ShopCustomAttributeDef;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopCustomAttributeDefRepository extends BaseRepository<ShopCustomAttributeDef, Long> {

    /**
     * Ordered list of active definitions for a shop — the shape the
     * frontend form renderer wants.
     */
    @Query("SELECT d FROM ShopCustomAttributeDef d WHERE d.shop.id = :shopId AND d.active = true ORDER BY d.displayOrder ASC, d.id ASC")
    List<ShopCustomAttributeDef> findAllActiveForShop(@Param("shopId") Long shopId);

    Optional<ShopCustomAttributeDef> findByShopIdAndKeyName(Long shopId, String keyName);

    boolean existsByShopIdAndKeyName(Long shopId, String keyName);
}
