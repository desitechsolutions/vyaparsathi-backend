package com.desitech.vyaparsathi.auth.repository;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    boolean existsByRole(Role role);

    boolean existsByEmail(String email);
}