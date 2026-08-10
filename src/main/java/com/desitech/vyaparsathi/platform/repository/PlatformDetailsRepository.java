package com.desitech.vyaparsathi.platform.repository;

import com.desitech.vyaparsathi.platform.entity.PlatformDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlatformDetailsRepository extends JpaRepository<PlatformDetails, Long> {

    /**
     * Fetch the top/single active platform details configuration.
     */
    Optional<PlatformDetails> findTopByOrderByIdAsc();
}
