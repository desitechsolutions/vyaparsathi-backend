package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Cacheable(value = "userDetails", key = "#username", cacheManager = "analyticsCacheManager")
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("User is inactive: " + username);
        }

        return new CustomUserDetails(user);
    }

    @CacheEvict(value = "userDetails", key = "#username", cacheManager = "analyticsCacheManager")
    public void evictUserCache(String username) {
        // Called on password change, deactivation, or role update
    }
}