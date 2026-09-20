package com.desitech.vyaparsathi.auth.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Dedicated configuration for the {@link PasswordEncoder} bean.
 *
 * <p>This class exists solely to break the circular dependency that would arise
 * if {@code passwordEncoder()} were defined inside {@link SecurityConfig}:
 *
 * <pre>
 *   SecurityConfig (owns @Bean passwordEncoder)
 *     └─@Autowired──▶ OAuthUserService
 *                       └─@constructor──▶ PasswordEncoder  ← SecurityConfig still creating!
 * </pre>
 *
 * By placing the bean in its own, dependency-free {@code @Configuration},
 * Spring can fully instantiate it before either {@link SecurityConfig} or
 * {@code OAuthUserService} are created, so the cycle never forms.
 */
@Configuration
public class PasswordEncoderConfig {

    /**
     * BCrypt at strength 12.
     * Enterprise minimum recommended by OWASP is ≥ 10; strength 12 takes
     * ~250 ms on a modern CPU per hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
