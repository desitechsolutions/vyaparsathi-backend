package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.auth.service.SessionDenylistService;
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
    private final SessionDenylistService sessionDenylistService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   CustomUserDetailsService userDetailsService,
                                   ImpersonationSessionCacheManager impersonationSessionCacheManager,
                                   ShopStatusCacheManager shopStatusCacheManager,
                                   SessionDenylistService sessionDenylistService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.impersonationSessionCacheManager = impersonationSessionCacheManager;
        this.shopStatusCacheManager = shopStatusCacheManager;
        this.sessionDenylistService = sessionDenylistService;
    }

    /**
     * Skip JWT processing for OAuth2 authorization and callback URLs.
     *
     * <p>Spring Security's OAuth2 filter chain handles these paths internally.
     * If the JWT filter runs on them it can commit the response (e.g. write a
     * 401 JSON body) before the OAuth2 redirect is issued, causing a 500
     * Whitelabel Error Page on the callback round-trip.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/");
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
                    // MFA challenge tokens are not access tokens. Reject them
                    // outright so a stolen challenge token can't be used to
                    // reach protected endpoints.
                    if (jwtUtil.isMfaChallengeToken(jwt)) {
                        logger.warn("Rejecting MFA challenge token used as access token on {}", request.getRequestURI());
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"MFA_PENDING\", \"message\": \"Complete MFA verification to continue.\"}");
                        return;
                    }

                    // Session denylist check — kills revoked sessions
                    // in milliseconds instead of waiting for the access
                    // token to expire. Tokens without a `sid` claim
                    // (legacy / impersonation) skip this check and rely
                    // on their normal expiry.
                    String sid = jwtUtil.extractSessionId(jwt);
                    if (sid != null && sessionDenylistService.isRevoked(sid)) {
                        logger.info("Rejecting revoked session {} on {}", sid, request.getRequestURI());
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"error\": \"SESSION_REVOKED\", \"message\": \"This session has been revoked. Please sign in again.\"}");
                        return;
                    }

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

            // 3. Set Spring Security Authentication if we have a valid JWT username and auth is missing or not a UserDetails principal
            org.springframework.security.core.Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
            if (username != null && (existingAuth == null
                    || existingAuth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken
                    || !(existingAuth.getPrincipal() instanceof UserDetails))) {
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
            // Log full detail server-side; return a generic message to the client
            // so internal exception text is never disclosed.
            logger.error("Security Filter Error for URI {}: {}", request.getRequestURI(), e.getMessage(), e);
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"AUTHENTICATION_FAILED\", \"message\": \"Authentication failed. Please sign in again.\"}");
        } finally {
            TenantContext.clear();
            ImpersonationContext.clear();
        }
    }
}