package com.desitech.vyaparsathi.subscriptions.razorpay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of legal buyer entity details associated with a subscription invoice.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInvoiceSnapshotDto {
    private Long shopId;
    private String shopName;
    private String ownerName;
    private String address;
    private String city;
    private String state;
    private String stateCode;
    private String pincode;
    private String gstin;
    private String phone;
    private String email;
}
