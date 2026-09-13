package com.desitech.vyaparsathi.compliance.dto;

import java.time.Instant;

public class PeriodLockDto {

    private int year;
    private int month;
    private String formType;
    private String status;
    private Instant lockedAt;
    private String lockedBy;
    private String reason;

    public static PeriodLockDto open(int year, int month) {
        PeriodLockDto dto = new PeriodLockDto();
        dto.year = year;
        dto.month = month;
        dto.formType = "ALL";
        dto.status = "OPEN";
        return dto;
    }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
