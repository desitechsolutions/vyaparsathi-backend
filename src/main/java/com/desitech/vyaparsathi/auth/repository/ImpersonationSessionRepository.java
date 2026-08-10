package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.ImpersonationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImpersonationSessionRepository extends JpaRepository<ImpersonationSession, Long> {
    Optional<ImpersonationSession> findBySessionUuid(String sessionUuid);
}
