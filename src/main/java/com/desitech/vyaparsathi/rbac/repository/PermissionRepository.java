package com.desitech.vyaparsathi.rbac.repository;

import com.desitech.vyaparsathi.common.annotations.SkipShopFilter;
import com.desitech.vyaparsathi.rbac.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@SkipShopFilter
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByOrderByModuleAscCodeAsc();
}
