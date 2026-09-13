package com.desitech.vyaparsathi.compliance.dto;

public class PeriodLockRequestDto {

    private int year;
    private int month;
    private String formType = "ALL";
    private String reason;

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public String getFormType() { return formType; }
    public void setFormType(String formType) { this.formType = formType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
