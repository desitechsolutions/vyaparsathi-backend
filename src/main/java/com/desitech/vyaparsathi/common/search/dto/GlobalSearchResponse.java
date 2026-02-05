package com.desitech.vyaparsathi.common.search.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class GlobalSearchResponse {
    private String id;        // Entity ID
    private String title;     // e.g., "Birendra Shaw" or "INV-001"
    private String subtitle;  // e.g., "9876543210" or "Balance: ₹500"
    private String type;      // CUSTOMER, SALE, ITEM
    private String route;     // Frontend navigation path
    private Map<String, Object> extraInfo; // For "Add Payment" or "View Details" buttons
}