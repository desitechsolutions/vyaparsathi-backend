package com.desitech.vyaparsathi.inventory.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Drug scheduling classification used in pharmacy management.
 * Based on Indian Drugs and Cosmetics Act scheduling.
 */
public enum DrugSchedule {

    /**
     * Schedule X – Habit-forming drugs (e.g., opioids, benzodiazepines).
     * Requires prescription; strict record-keeping.
     */
    SCHEDULE_X("SCHEDULE_X"),

    /**
     * Schedule H – Prescription-only medicines not sold without a valid prescription.
     */
    SCHEDULE_H("SCHEDULE_H"),

    /**
     * Schedule H1 – More stringent prescription drugs (e.g., third-generation antibiotics, anti-TB drugs).
     * Requires storage of prescription copy.
     */
    SCHEDULE_H1("SCHEDULE_H1"),

    /**
     * Non-scheduled prescription drugs – Require a prescription but not covered by Schedule H/H1/X.
     */
    NON_SCHEDULED("NON_SCHEDULED"),

    /**
     * Over-the-counter medicines – Can be sold without a prescription.
     */
    OTC("OTC");

    private final String value;

    DrugSchedule(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DrugSchedule fromValue(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        for (DrugSchedule schedule : DrugSchedule.values()) {
            if (schedule.value.equalsIgnoreCase(value.trim())) {
                return schedule;
            }
        }
        throw new IllegalArgumentException("Unknown DrugSchedule: " + value);
    }
}
