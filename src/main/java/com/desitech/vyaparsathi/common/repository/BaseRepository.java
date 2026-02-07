package com.desitech.vyaparsathi.common.repository;

import com.desitech.vyaparsathi.common.entities.BaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseRepository<T extends BaseEntity, ID> extends JpaRepository<T, ID> {
    // Optional default methods for enabling shop filter, if needed
}
