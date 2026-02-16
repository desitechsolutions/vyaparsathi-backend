package com.desitech.vyaparsathi.subscriptions.dto;

import com.desitech.vyaparsathi.subscriptions.enums.Tier;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PendingPaymentDTO {
    private Long id;
    private String utrNumber;
    private Double amount;
    private Tier planRequested;
    private LocalDateTime submittedAt;

    // Shop Details
    private Long shopId;
    private String shopName;

    // User/Owner Details
    private Long userId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
}