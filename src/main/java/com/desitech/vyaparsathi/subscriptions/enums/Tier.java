package com.desitech.vyaparsathi.subscriptions.enums;

public enum Tier {
    STARTER, PRO, ENTERPRISE, FREE;

    public static Tier fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return FREE;
        }
        try {
            return Tier.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
}

