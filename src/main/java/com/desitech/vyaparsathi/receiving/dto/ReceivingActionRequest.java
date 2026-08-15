package com.desitech.vyaparsathi.receiving.dto;

/**
 * Body payload for the confirm / approve / dispute endpoints introduced in
 * Phase 6.1. All three share the same shape — a free-form note explaining
 * the action — so a single DTO keeps controller signatures uniform.
 */
public class ReceivingActionRequest {

    private String note;

    public ReceivingActionRequest() {}

    public ReceivingActionRequest(String note) {
        this.note = note;
    }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}