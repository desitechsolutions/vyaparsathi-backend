package com.desitech.vyaparsathi.common.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GlobalSearchResponse {
    private String id;        // Entity ID
    private String title;     // e.g., "Birendra Shaw" or "INV-001"
    private String subtitle;  // e.g., "9876543210" or "Balance: ₹500"
    private String type;      // CUSTOMER, SALE, ITEM
    private String route;     // Frontend navigation path
    private Map<String, Object> extraInfo; // For "Add Payment" or "View Details" buttons

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRoute() { return route; }
    public void setRoute(String route) { this.route = route; }

    public Map<String, Object> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, Object> extraInfo) { this.extraInfo = extraInfo; }
}