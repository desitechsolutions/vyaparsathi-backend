package com.desitech.vyaparsathi.auth.controller;

import com.desitech.vyaparsathi.auth.dto.*;
import com.desitech.vyaparsathi.auth.service.UserManagementService;
import com.desitech.vyaparsathi.common.exception.ApplicationException;
import com.desitech.vyaparsathi.rbac.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserManagementController {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

    @Autowired
    private UserManagementService userManagementService;

    @GetMapping
    @RequirePermission("TEAM_VIEW")
    public List<UserDto> listUsers() {
        logger.info("Request to list all users received.");
        return userManagementService.listAllUsers();
    }

    // Endpoint for Admins/Owners to create a new user
    @PostMapping
    @RequirePermission("TEAM_INVITE")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody RegisterRequest request) {
        try {
            UserDto newUser = userManagementService.createUser(request);
            logger.info("Admin created new user: {}", request.getUsername());
            return new ResponseEntity<>(newUser, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error creating user {}: {}", request.getUsername(), e.getMessage(), e);
            // Consider a more specific exception mapping in a real app
            throw new ApplicationException("Failed to create user: " + e.getMessage(), e);
        }
    }

    @PatchMapping("/{id}/status")
    @RequirePermission("TEAM_MANAGE")
    public ResponseEntity<UserDto> changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        try {
            UserDto updatedUser = userManagementService.changeUserStatus(id, request.isActive());
            logger.info("Changed status for user id={}, active={}", id, request.isActive());
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            logger.error("Error changing status for user id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to change user status: " + e.getMessage(), e);
        }
    }

    @PatchMapping("/{id}/role")
    @RequirePermission("TEAM_MANAGE")
    public ResponseEntity<UserDto> changeRole(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        try {
            UserDto updatedUser = userManagementService.changeUserRole(id, request.getRole());
            logger.info("Changed role for user id={}, role={}", id, request.getRole());
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            logger.error("Error changing role for user id={}: {}", id, e.getMessage(), e);
            throw new ApplicationException("Failed to change user role: " + e.getMessage(), e);
        }
    }

    @PutMapping("/{id}")
    @RequirePermission("TEAM_MANAGE")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        UserDto updatedUser = userManagementService.updateUser(id, request);
        return ResponseEntity.ok(updatedUser);
    }

}