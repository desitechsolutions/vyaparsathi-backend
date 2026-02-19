package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.common.configs.TenantContext;
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
    //private final ShopFilterEnabler shopFilterEnabler;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   CustomUserDetailsService userDetailsService
                                   ) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        //this.shopFilterEnabler = shopFilterEnabler;
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
        String roleFromToken = null;

        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                jwt = authHeader.substring(7);
                logger.debug("Processing JWT for request: {}", request.getRequestURI());
                if (jwtUtil.validateToken(jwt)) {
                    username = jwtUtil.extractUsername(jwt);
                    shopId = jwtUtil.extractShopId(jwt);
                    roleFromToken = jwtUtil.extractRole(jwt);
                    logger.info("Extracted username: {}, shopId: {} from JWT", username, shopId);
                    if (shopId == null) {
                        if ("PENDING_OWNER".equals(roleFromToken)) {
                            logger.info("Allowing login without shopId for PENDING_OWNER: {}", username);
                            // Proceed — do not return 401
                        }
                        else if ("SUPER_ADMIN".equals(roleFromToken)) {
                            logger.info("Allowing login for SUPER_ADMIN: {}", username);
                            // Proceed — do not return 401
                        }
                        else{
                            logger.error("No shopId found in JWT for username: {}", username);
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write("Missing shopId in token");
                            return;
                        }

                    }
                    TenantContext.setCurrentShopId(shopId);
                    logger.info("Set shopId {} in TenantContext", shopId);
                    logger.info("Enabled shopFilter for shopId: {}", shopId);
                } else {
                    logger.warn("Invalid JWT token for request: {}", request.getRequestURI());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("Invalid token");
                    return;
                }
            } else {
                logger.debug("No Bearer token found in request: {}", request.getRequestURI());
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                logger.debug("Set authentication for username: {}", username);
            }
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error processing JWT filter for request: {}", request.getRequestURI(), e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Authentication error: " + e.getMessage());
        } finally {
            TenantContext.clear();
            logger.debug("Cleared TenantContext for request: {}", request.getRequestURI());
        }
    }
}