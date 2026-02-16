package com.desitech.vyaparsathi.subscriptions.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlatformStatsDTO {
    private long pendingCount;    // Number of WAITING payments
    private long totalShops;     // Total registered shops
    private double totalRevenue;  // Sum of all APPROVED payments
    private long totalUsers;      // Total users in the system
}