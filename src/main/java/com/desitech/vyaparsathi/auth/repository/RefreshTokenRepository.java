package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.RefreshToken;
import com.desitech.vyaparsathi.common.repository.BaseRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface RefreshTokenRepository extends BaseRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUsername(String username);
}