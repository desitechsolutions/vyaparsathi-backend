package com.desitech.vyaparsathi.customer.dto;

import lombok.Data;

/**
 * Inbound filter DTO for the paginated customer list endpoint.
 * All fields are optional — null/blank means "no filter".
 */
@Data
public class CustomerFilterDto {
    private String search;
    private Boolean active;
    private String customerType;   // "INDIVIDUAL" or "BUSINESS"
    private String source;         // "WALK_IN", "REFERRAL", etc.
    private String city;
    private String tags;
    private int page = 0;
    private int size = 25;
    private String sortBy = "name";
    private String sortDir = "asc";
}
