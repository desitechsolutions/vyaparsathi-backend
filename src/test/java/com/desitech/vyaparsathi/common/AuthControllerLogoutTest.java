package com.desitech.vyaparsathi.common;

import com.desitech.vyaparsathi.auth.controller.AuthController;
import com.desitech.vyaparsathi.auth.service.AuthService;
import com.desitech.vyaparsathi.auth.service.EmailVerificationService;
import com.desitech.vyaparsathi.auth.service.PasswordResetTokenService;
import com.desitech.vyaparsathi.auth.service.RefreshTokenService;
import com.desitech.vyaparsathi.auth.service.SessionService;
import com.desitech.vyaparsathi.auth.service.UserManagementService;
import com.desitech.vyaparsathi.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for the {@code POST /api/auth/logout} endpoint's cookie-clearing behaviour.
 *
 * <p>The primary regression this guards against: a mismatch between the
 * {@code Set-Cookie} attributes used when <em>setting</em> the refresh-token
 * cookie (at login) and those used when <em>clearing</em> it (at logout).
 * Browsers use {@code name + path + domain} as the composite cookie identity;
 * if those attributes differ, the {@code Max-Age=0} directive is silently
 * ignored and the cookie survives logout, allowing the frontend silentRefresh()
 * to re-authenticate the user on the next page load.</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLogoutTest {

    @InjectMocks
    private AuthController authController;

    @Mock private AuthService authService;
    @Mock private PasswordResetTokenService resetTokenService;
    @Mock private UserManagementService userManagementService;
    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private SessionService sessionService;

    /**
     * Inject the @Value fields that would normally be populated by Spring's
     * property resolution. We use LOCAL profile values (insecure, Lax) because
     * the test environment doesn't run HTTPS.
     */
    @BeforeEach
    void injectCookieProperties() {
        ReflectionTestUtils.setField(authController, "cookieSecure",   false);
        ReflectionTestUtils.setField(authController, "cookieSameSite", "Lax");
        ReflectionTestUtils.setField(authController, "cookiePath",     "/");
    }

    // -------------------------------------------------------------------------

    private String extractSetCookieHeader(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies)
                .as("Response must contain exactly one Set-Cookie header")
                .hasSize(1);
        return cookies.get(0);
    }

    // -------------------------------------------------------------------------

    @Test
    @DisplayName("logout() sets Max-Age=0 to instruct the browser to delete the cookie")
    void logout_setsCookieMaxAgeToZero() {
        doNothing().when(authService).logout("valid-token");

        ResponseEntity<?> response = authController.logout("valid-token");

        String setCookie = extractSetCookieHeader(response);
        assertThat(setCookie).contains("refreshToken=");
        assertThat(setCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    @DisplayName("logout() clear-cookie uses Path=/ — matching the login cookie so the browser can evict it")
    void logout_clearCookieUsesMatchingPath() {
        doNothing().when(authService).logout("valid-token");

        ResponseEntity<?> response = authController.logout("valid-token");

        String setCookie = extractSetCookieHeader(response);
        assertThat(setCookie).contains("Path=/");
    }

    @Test
    @DisplayName("logout() clear-cookie SameSite attribute matches the injected profile value")
    void logout_clearCookieSameSiteMatchesProfile() {
        doNothing().when(authService).logout("valid-token");

        ResponseEntity<?> response = authController.logout("valid-token");

        String setCookie = extractSetCookieHeader(response);
        assertThat(setCookie).containsIgnoringCase("SameSite=Lax");
    }

    @Test
    @DisplayName("logout() clear-cookie Secure flag is absent when cookieSecure=false (local/test profile)")
    void logout_clearCookieSecureFlagAbsentForInsecureProfile() {
        doNothing().when(authService).logout("valid-token");

        ResponseEntity<?> response = authController.logout("valid-token");

        String setCookie = extractSetCookieHeader(response);
        // On local HTTP, the Secure attribute must be absent — if it were
        // present, the browser would silently ignore the entire Set-Cookie.
        assertThat(setCookie).doesNotContain("Secure");
    }

    @Test
    @DisplayName("logout() with Secure=true profile includes Secure attribute")
    void logout_clearCookieSecureFlagPresentForSecureProfile() {
        ReflectionTestUtils.setField(authController, "cookieSecure",   true);
        ReflectionTestUtils.setField(authController, "cookieSameSite", "None");
        doNothing().when(authService).logout("prod-token");

        ResponseEntity<?> response = authController.logout("prod-token");

        String setCookie = extractSetCookieHeader(response);
        assertThat(setCookie).contains("Secure");
        assertThat(setCookie).containsIgnoringCase("SameSite=None");
    }

    @Test
    @DisplayName("logout() delegates to AuthService.logout with the supplied refresh token")
    void logout_delegatesToAuthService() {
        doNothing().when(authService).logout("token-abc");

        authController.logout("token-abc");

        verify(authService).logout("token-abc");
    }

    @Test
    @DisplayName("logout() still returns 200 with a clear-cookie when no refreshToken cookie is present")
    void logout_noCookiePresent_returnsOkWithClearCookie() {
        doNothing().when(authService).logout(null);

        ResponseEntity<?> response = authController.logout(null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Even without a cookie, we emit the clear-cookie header so any
        // orphan cookie lingering from a prior session is evicted.
        String setCookie = extractSetCookieHeader(response);
        assertThat(setCookie).containsIgnoringCase("Max-Age=0");
    }

    @Test
    @DisplayName("logout() response body signals success")
    void logout_responseBodySignalsSuccess() {
        doNothing().when(authService).logout("token-xyz");

        ResponseEntity<?> response = authController.logout("token-xyz");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).asString().containsIgnoringCase("logged out");
    }
}
