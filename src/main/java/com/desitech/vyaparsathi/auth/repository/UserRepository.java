package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends BaseRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByRole(Role role);
    boolean existsByEmail(String email);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameAndShop_Id(String username, Long shopId);
    Optional<User> findFirstByShopOrderByCreatedAtAsc(Shop shop);

    List<User> findByShopId(Long shopId);
}