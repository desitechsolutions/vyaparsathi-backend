package com.desitech.vyaparsathi.config;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${initial.owner.username}")
    private String ownerUsername;

    @Value("${initial.owner.pin}")
    private String ownerPin;

    @Override
    public void run(String... args) throws Exception {
        // Check if an OWNER already exists to prevent running this every time
        if (userRepository.existsByRole(Role.OWNER)) {
            logger.info("Owner user already exists. Skipping initial data seed.");
        } else {
            logger.info("No OWNER found. Creating initial owner account...");
            User owner = new User();
            owner.setUsername(ownerUsername);
            owner.setPinHash(passwordEncoder.encode(ownerPin));
            owner.setRole(Role.OWNER);
            owner.setActive(true);

            userRepository.save(owner);
            logger.info("Initial OWNER account created successfully with username: {}", ownerUsername);
        }
    }
}