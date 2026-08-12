package com.desitech.vyaparsathi.refund.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundTokenData {
    private Long refundId;
    private String refundNo;
}
