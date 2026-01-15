package com.desitech.vyaparsathi.auth.controller;

import com.desitech.vyaparsathi.auth.service.UserManagementService;
import com.desitech.vyaparsathi.shop.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenants")
public class AdminController {

    @Autowired
    private ShopService shopService;

    @Autowired
    private UserManagementService userManagementService;

    /*@PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> setupTenant(@RequestBody TenantSetupRequest request) {
        try {
            // Create Shop
            TenantContext.setCurrentShopId(null); // Clear TenantContext
            ShopDto shopDto = request.getShopDto();
            ShopDto createdShop = shopService.createInitialShop(shopDto);

            // Create OWNER user
            TenantContext.setCurrentShopId(createdShop.getId());
            RegisterRequest ownerRequest = request.getOwnerRequest();
            userManagementService.createUser(ownerRequest);

            TenantContext.clear(); // Clear TenantContext
            return ResponseEntity.ok("Tenant setup completed for shopId: " + createdShop.getId());
        } catch (Exception e) {
            throw new ApplicationException("Failed to setup tenant: " + e.getMessage(), e);
        }
    }*/
}