package com.desitech.vyaparsathi.auth.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum Role {
    OWNER,
    STAFF,
    ADMIN,
    SUPER_ADMIN,
    PENDING_OWNER;

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty. Allowed values: " + Arrays.toString(Role.values()));
        }
        try {
            return Role.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + value + ". Allowed values: " + Arrays.toString(Role.values()));
        }
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}