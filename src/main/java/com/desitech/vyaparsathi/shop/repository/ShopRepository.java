package com.desitech.vyaparsathi.shop.repository;

import com.desitech.vyaparsathi.shop.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
        Boolean existsByCode(String code);
}