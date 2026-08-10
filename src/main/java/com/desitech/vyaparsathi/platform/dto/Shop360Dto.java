package com.desitech.vyaparsathi.platform.dto;

import com.desitech.vyaparsathi.audit.entity.AuditLog;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class Shop360Dto {
    private Long shopId;
    private String shopName;
    private String shopCode;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String gstin;
    private String address;
    private String state;
    private boolean active;
    private LocalDateTime createdAt;
    private long userCount;
    private String currentPlanCode;
    private String subscriptionStatus;
    private String billingCycle;
    private String razorpaySubscriptionId;
    private LocalDateTime currentPeriodEnd;
    private List<AuditLog> lifecycleLogs;
}
