package com.desitech.vyaparsathi.auth.dto;

import java.time.LocalDateTime;

/**
 * Session projection returned by GET /api/auth/sessions. The
 * {@link #current} flag is set for the row that matches the JWT
 * currently making the request — the UI uses it to grey-out the
 * "Revoke this session" button and label the row "This device".
 */
public class UserSessionDto {

    private String sessionId;
    private String deviceLabel;
    private String userAgent;
    private String ipAddress;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private boolean current;

    public UserSessionDto() {}

    public UserSessionDto(String sessionId, String deviceLabel, String userAgent, String ipAddress,
                          LocalDateTime createdAt, LocalDateTime lastActiveAt, boolean current) {
        this.sessionId = sessionId;
        this.deviceLabel = deviceLabel;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.current = current;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getDeviceLabel() { return deviceLabel; }
    public void setDeviceLabel(String deviceLabel) { this.deviceLabel = deviceLabel; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(LocalDateTime lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }
}
