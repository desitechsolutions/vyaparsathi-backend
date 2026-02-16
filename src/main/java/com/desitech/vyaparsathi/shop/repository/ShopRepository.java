package com.desitech.vyaparsathi.shop.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
        Boolean existsByCode(String code);
        @Query(value = "SELECT COUNT(*) FROM shop WHERE code = :code", nativeQuery = true)
        Long countByCodeGlobal(@Param("code") String code);
}