package com.desitech.vyaparsathi.auth.security.oauth2;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Runs after Spring Security completes the OAuth2 authorization-code exchange
 * and {@link OAuthUserService} has loaded / created our {@link User}.
 *
 * <p>Issues our standard JWT access token and refresh-token cookie, then
 * redirects the browser to the frontend callback page where the SPA picks
 * up the token.
 */
@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final Duration REFRESH_COOKIE_TTL = Duration.ofDays(7);

    private final JwtUtil jwtUtil;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.oauth2.frontend-redirect-uri:http://localhost:3000/auth/oauth2/callback}")
    private String frontendRedirectUri;

    @Value("${app.auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${app.auth.cookie.same-site:Lax}")
    private String cookieSameSite;

    public OAuth2AuthenticationSuccessHandler(JwtUtil jwtUtil,
                                              RefreshTokenService refreshTokenService) {
        this.jwtUtil = jwtUtil;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OAuthPrincipal principal)) {
            // Should never happen if both userService and oidcUserService are wired correctly,
            // but guard against regressions (e.g. a provider returning DefaultOidcUser).
            log.error("OAuth2 success handler received unexpected principal type: {}",
                    authentication.getPrincipal().getClass().getName());
            getRedirectStrategy().sendRedirect(request, response,
                    frontendRedirectUri + "?error=oauth2_internal_error");
            return;
        }
        User user = principal.getUser();

        // Issue access token
        Long shopId = user.getShop() != null ? user.getShop().getId() : null;
        String accessToken = jwtUtil.generateAccessToken(user, shopId);

        // Issue refresh token + set HttpOnly cookie
        String rawRefresh = refreshTokenService.createRefreshToken(user.getUsername()).getToken();
        ResponseCookie refreshCookie = ResponseCookie.from(REFRESH_COOKIE_NAME, rawRefresh)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/api/auth")
                .sameSite(cookieSameSite)
                .maxAge(REFRESH_COOKIE_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // isNewUser = user has not set up a shop yet
        boolean isNewUser = user.getShop() == null;

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("token", URLEncoder.encode(accessToken, StandardCharsets.UTF_8))
                .queryParam("new", isNewUser)
                .build(true)
                .toUriString();

        log.info("OAuth2 login success for {} — redirecting to frontend (isNew={})",
                user.getUsername(), isNewUser);

        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
