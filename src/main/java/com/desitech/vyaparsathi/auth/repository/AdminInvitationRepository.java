package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.AdminInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminInvitationRepository extends JpaRepository<AdminInvitation, Long> {
    Optional<AdminInvitation> findByTokenHash(String tokenHash);
    Optional<AdminInvitation> findByEmail(String email);
    boolean existsByEmailAndStatus(String email, String status);
}
