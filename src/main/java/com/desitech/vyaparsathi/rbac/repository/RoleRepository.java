package com.desitech.vyaparsathi.rbac.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.rbac.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Roles are shop-scoped but we skip the tenant filter here because RBAC
 * lookups always specify the shopId explicitly (from the JWT context
 * or from admin-boot code that iterates every shop).
 */
@SkipShopFilter
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByShopIdAndName(Long shopId, String name);

    List<Role> findByShopId(Long shopId);

    List<Role> findByShopIdAndSystem(Long shopId, boolean system);
}
