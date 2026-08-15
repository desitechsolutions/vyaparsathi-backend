package com.desitech.vyaparsathi.purchasereturn.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Signed-URL payload for the Purchase Return PDF endpoint. Mirrors the
 * {@code ReceivingTokenData} shape so the shared JWT machinery works
 * identically across GRN, PO and RTV documents.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseReturnTokenData {
    private Long purchaseReturnId;
    private String returnNo;
}
