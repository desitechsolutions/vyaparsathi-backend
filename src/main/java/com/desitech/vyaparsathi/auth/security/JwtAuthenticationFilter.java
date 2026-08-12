package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.common.configs.TenantContext;
import com.desitech.vyaparsathi.platform.service.ImpersonationSessionCacheManager;
import com.desitech.vyaparsathi.platform.service.ShopStatusCacheManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final ImpersonationSessionCacheManager impersonationSessionCacheManager;
    private final ShopStatusCacheManager shopStatusCacheManager;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   CustomUserDetailsService userDetailsService,
                                   ImpersonationSessionCacheManager impersonationSessionCacheManager,
                                   ShopStatusCacheManager shopStatusCacheManager) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.impersonationSessionCacheManager = impersonationSessionCacheManager;
        this.shopStatusCacheManager = shopStatusCacheManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;
        Long shopId = null;

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
                logger.debug("Processing JWT for request: {}", request.getRequestURI());

                if (jwtUtil.validateToken(jwt)) {
                    username = jwtUtil.extractUsername(jwt);
                    shopId = jwtUtil.extractShopId(jwt);

                    // 1. Impersonation Session Validation & Lockdown
                    if (Boolean.TRUE.equals(jwtUtil.isImpersonationToken(jwt))) {
                        String sessionUuid = jwtUtil.extractImpersonationSessionId(jwt);
                        var validationResult = impersonationSessionCacheManager.validateSession(sessionUuid);

                        if (validationResult != ImpersonationSessionCacheManager.ValidationResult.VALID) {
                            logger.warn("Rejecting impersonation request for session {}: {}", sessionUuid, validationResult);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"UNAUTHORIZED\", \"message\": \"Impersonation session has ended or expired.\"}");
                            return;
                        }

                        // Block impersonated token from accessing admin endpoints (except exit impersonation)
                        if (request.getRequestURI().startsWith("/api/admin/") && !request.getRequestURI().equals("/api/admin/operations/impersonate/exit")) {
                            logger.warn("Blocked impersonated token attempt on admin URI: {}", request.getRequestURI());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"FORBIDDEN\", \"message\": \"Impersonated sessions cannot perform platform admin operations.\"}");
                            return;
                        }

                        // Set ImpersonationContext ThreadLocal for dual-identity forensic audit linkage
                        Long originalAdminId = jwtUtil.extractClaim(jwt, claims -> claims.get("originalAdminId", Long.class));
                        ImpersonationContext.set(originalAdminId, sessionUuid, shopId);
                    }

                    // 2. Instant Shop Suspension Guard for Merchant APIs.
                    //    Only the SUSPENSION check is skipped for /api/admin/* routes
                    //    — those are platform-level controllers (super-admin ops on
                    //    tenants, entitlements, feature flags) which must remain
                    //    reachable even when a shop is suspended.
                    if (shopId != null && !request.getRequestURI().startsWith("/api/admin/")) {
                        if (!shopStatusCacheManager.isShopActive(shopId)) {
                            logger.warn("Blocked request for suspended shopId: {}", shopId);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\": \"SHOP_SUSPENDED\", \"message\": \"Shop account has been suspended by platform administration.\"}");
                            return;
                        }
                    }

                    // 3. Tenant context — MUST be set for every authenticated request
                    //    that carries a shop-scoped JWT, regardless of URL. Not all
                    //    /api/admin/* URLs are platform-level: /api/admin/users is a
                    //    shop-scoped controller (staff management inside the shop),
                    //    and its service reads TenantContext.getCurrentShopId() to
                    //    filter results. Bundling this line inside the suspension
                    //    check above caused an empty user list for shop admins.
                    if (shopId != null) {
                        TenantContext.setCurrentShopId(shopId);
                    }
                } else {
                    logger.debug("Invalid or expired JWT encountered for: {}", request.getRequestURI());
                }
            }

            // 3. Set Spring Security Authentication if we have a username and no existing auth
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (userDetails != null) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()
                            );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

            // 4. Continue the filter chain
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Security Filter Error for URI {}: {}", request.getRequestURI(), e.getMessage());
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Authentication failed\", \"message\": \"" + e.getMessage() + "\"}");
        } finally {
            TenantContext.clear();
            ImpersonationContext.clear();
        }
    }
}