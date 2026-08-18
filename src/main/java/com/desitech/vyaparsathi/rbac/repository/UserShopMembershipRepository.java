package com.desitech.vyaparsathi.rbac.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.rbac.entity.UserShopMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@SkipShopFilter
@Repository
public interface UserShopMembershipRepository extends JpaRepository<UserShopMembership, Long> {

    List<UserShopMembership> findByUserId(Long userId);

    List<UserShopMembership> findByUserIdAndActiveTrue(Long userId);

    List<UserShopMembership> findByShopIdAndActiveTrue(Long shopId);

    Optional<UserShopMembership> findByUserIdAndShopId(Long userId, Long shopId);

    long countByUserIdAndActiveTrue(Long userId);

    long countByShopIdAndActiveTrue(Long shopId);
}
