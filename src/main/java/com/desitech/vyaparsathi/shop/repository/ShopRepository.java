package com.desitech.vyaparsathi.shop.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.shop.dto.GlobalShopSummaryDTO;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@SkipShopFilter
public interface ShopRepository extends JpaRepository<Shop, Long> {

        Boolean existsByCode(String code);

        @Query(value = "SELECT COUNT(*) FROM shop WHERE code = :code", nativeQuery = true)
        Long countByCodeGlobal(@Param("code") String code);

        @Query("SELECT new com.desitech.vyaparsathi.shop.dto.GlobalShopSummaryDTO(" +
                "s.id, " +
                "s.name, " +
                "s.code, " +
                "s.state, " +
                "s.createdAt, " +
                "CONCAT(COALESCE(u.firstName, ''), ' ', COALESCE(u.lastName, '')), " +
                "u.email, " +
                "s.active, " +
                "sub.tier, " +
                "sub.status, " +
                "sub.endDate) " +
                "FROM Shop s " +
                "LEFT JOIN User u ON u.shop = s AND (u.role = com.desitech.vyaparsathi.auth.model.Role.OWNER OR u.role = com.desitech.vyaparsathi.auth.model.Role.ADMIN) " +
                "LEFT JOIN Subscription sub ON sub.shop = s " +
                "ORDER BY s.createdAt DESC")
        Page<GlobalShopSummaryDTO> findAllShopSummaries(Pageable pageable);
}