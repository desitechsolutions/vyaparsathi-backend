package com.desitech.vyaparsathi.auth.service;

import com.desitech.vyaparsathi.auth.dto.RegisterRequest;
import com.desitech.vyaparsathi.auth.dto.UpdateUserRequest;
import com.desitech.vyaparsathi.auth.dto.UserDto;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserManagementService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public UserDto createUser(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        // Security: Prevent Admin from creating an Owner
        if (request.getRole() == Role.OWNER) {
            throw new SecurityException("Admins cannot create users with the OWNER role.");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email address is already in use");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPinHash(passwordEncoder.encode(request.getPin()));
        user.setRole(request.getRole());
        user.setActive(true); // Default to active
        User savedUser = userRepository.save(user);
        return toUserDto(savedUser);
    }

    @Transactional
    public UserDto updateUser(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        // Prevent OWNER from being modified in any way other than by themselves (future feature)
        if (user.getRole() == Role.OWNER) {
            // For now, let's just protect their core details from other admins
            // You could add more complex logic here later
        }

        // Check for email uniqueness if it's being changed
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email address is already in use by another user.");
            }
            user.setEmail(request.getEmail());
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        if (request.getShopId() != null) {
            Shop shop = shopRepository.findById(request.getShopId())
                    .orElseThrow(() -> new EntityNotFoundException("Shop not found with id: " + request.getShopId()));
            user.setShop(shop);
        } else {
            user.setShop(null); // Allow un-assigning from a shop
        }

        User updatedUser = userRepository.save(user);
        return toUserDto(updatedUser);
    }
    public List<UserDto> listAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toUserDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserDto changeUserStatus(Long userId, boolean active) {
        User userToModify = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Security: Prevent changing status of an OWNER or self-deactivation
        if (userToModify.getRole() == Role.OWNER) {
            throw new SecurityException("The status of an OWNER cannot be changed.");
        }
        if (userToModify.getUsername().equals(getAuthenticatedUsername())) {
            throw new SecurityException("You cannot change your own status.");
        }

        userToModify.setActive(active);
        User updatedUser = userRepository.save(userToModify);
        return toUserDto(updatedUser);
    }

    @Transactional
    public UserDto changeUserRole(Long userId, Role newRole) {
        User userToModify = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        // Security: Prevent changing role of an OWNER or self-modification
        if (userToModify.getRole() == Role.OWNER) {
            throw new SecurityException("The role of an OWNER cannot be changed.");
        }
        if (userToModify.getUsername().equals(getAuthenticatedUsername())) {
            throw new SecurityException("You cannot change your own role.");
        }
        // Security: Prevent escalation to OWNER
        if (newRole == Role.OWNER) {
            throw new SecurityException("Cannot assign the OWNER role.");
        }

        userToModify.setRole(newRole);
        User updatedUser = userRepository.save(userToModify);
        return toUserDto(updatedUser);
    }

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }
        return authentication.getName();
    }
    private UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole().name());
        dto.setActive(user.isActive());

        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setCreatedAt(user.getCreatedAt());

        if (user.getShop() != null) {
            dto.setShopId(user.getShop().getId());
            dto.setShopName(user.getShop().getName());
        }

        return dto;
    }

}
