package com.desitech.vyaparsathi.auth.dto;

import com.desitech.vyaparsathi.shop.dto.ShopDto;
import lombok.Data;

@Data
public class TenantSetupRequest {
    private ShopDto shopDto;
    private RegisterRequest ownerRequest;
}
