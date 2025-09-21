package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.common.util.TenantContext;
import com.desitech.vyaparsathi.shop.entity.Shop;
import com.desitech.vyaparsathi.shop.repository.ShopRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TenantFilter extends OncePerRequestFilter {
    @Autowired
    private ShopRepository shopRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String shopCode = extractShopCode(path);
        if (shopCode != null) {
            Long shopId = shopRepository.findByCode(shopCode)
                    .map(Shop::getId)
                    .orElseThrow(() -> new IllegalStateException("Invalid tenant: " + shopCode));
            TenantContext.setTenantId(shopId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractShopCode(String path) {
        // Example: /api/shops/shop1/customers -> extract "shop1"
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].equals("shops") && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null; // No tenant specified; handle in controllers if needed
    }
}