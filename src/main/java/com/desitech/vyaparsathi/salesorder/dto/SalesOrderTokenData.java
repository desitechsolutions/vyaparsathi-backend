package com.desitech.vyaparsathi.salesorder.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderTokenData {
    private Long salesOrderId;
    private String orderNo;
}
