package com.desitech.vyaparsathi.platform.repository;

import com.desitech.vyaparsathi.platform.entity.PlatformFeatureFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformFeatureFlagRepository extends JpaRepository<PlatformFeatureFlag, String> {
}
