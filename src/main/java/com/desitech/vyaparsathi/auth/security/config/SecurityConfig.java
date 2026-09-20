package com.desitech.vyaparsathi.auth.security.config;

import com.desitech.vyaparsathi.auth.security.JwtAuthenticationFilter;
import com.desitech.vyaparsathi.auth.security.RateLimitFilter;
import com.desitech.vyaparsathi.auth.security.oauth2.OAuthUserService;
import com.desitech.vyaparsathi.auth.security.oauth2.OidcOAuthUserService;
import com.desitech.vyaparsathi.auth.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.desitech.vyaparsathi.auth.security.oauth2.OAuth2AuthenticationFailureHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Autowired
    private OAuthUserService oAuthUserService;

    @Autowired
    private OidcOAuthUserService oidcOAuthUserService;

    @Autowired
    private OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;

    @Autowired
    private OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

    @Value("${app.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                // OAuth2 provider redirect URIs — must be public
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/media/**",
                                // Signed-URL PDF endpoints — access is gated by a document-specific
                                // JWT embedded in the URL, not by session auth. The URL filter must
                                // allow the request through so the controller can validate the token.
                                "/api/invoices/signed",
                                "/api/receipts/signed",
                                "/api/refunds/signed",
                                "/api/quotations/signed",
                                "/api/sales-orders/signed",
                                "/api/purchase-orders/signed",
                                "/api/receiving/signed",
                                "/api/purchase-returns/signed",
                                "/api/deliveries/challan/signed",
                                "/api/v1/credit-notes/signed",
                                "/api/v1/debit-notes/signed",
                                "/ws/**",
                                "/api/notifications/public/**",
                                "/api/newsletter/subscribe",
                                "/api/newsletter/unsubscribe",
                                // Shop invitation preview + accept are public so an invitee
                                // (not yet signed in) can look up + accept via the emailed link.
                                "/api/shop/invitations/lookup",
                                "/api/shop/invitations/accept",
                                "/uploads/**",
                                "/api/files/**",
                                // Razorpay server-to-server webhook — must be public (no JWT)
                                "/api/webhooks/**",
                                // Public platform info and public endpoints
                                "/api/v1/public/**",
                                // Offline queue health check for connectivity probing
                                "/api/sales/offline-queue/health",
                                // Public pricing — must be accessible to unauthenticated users
                                // (landing page, public pricing page, guest checkout flow)
                                "/api/pricing/active"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                // OAuth2 login requires a session for the authorization request/state.
                // We keep STATELESS for every other request so the existing JWT flow
                // is unaffected; Spring Security creates a transient session only during
                // the provider redirect → callback round-trip.
                .sessionManagement(sess -> sess
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(ui -> ui
                                .userService(oAuthUserService)          // non-OIDC OAuth2 providers
                                .oidcUserService(oidcOAuthUserService)) // OIDC providers (Google with openid scope)
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler))
                // Rate limit BEFORE the JWT filter — attackers hitting /api/auth/** are
                // by definition unauthenticated, so the check must run at the door.
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Explicitly list origins. Wildcards (*) fail when allowCredentials is true.
        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        configuration.setAllowedOrigins(origins);

        // 2. Methods allowed
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 3. IMPORTANT: Explicitly list headers instead of "*".
        // Wildcards in AllowedHeaders often block the request when using Credentials.
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Razorpay-Signature"
        ));

        // 4. Headers the frontend can see
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie"));

        // 5. Allow cookies
        configuration.setAllowCredentials(true);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}