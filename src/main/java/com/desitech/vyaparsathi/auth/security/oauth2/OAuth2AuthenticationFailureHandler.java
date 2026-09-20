package com.desitech.vyaparsathi.auth.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Redirects the browser to the frontend login page with an {@code oauth2Error}
 * query parameter when an OAuth2 login fails (user denied access, missing
 * email scope, unsupported provider, etc.).
 */
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    @Value("${app.oauth2.frontend-login-uri:http://localhost:3000/login}")
    private String frontendLoginUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String message = exception.getMessage() != null
                ? exception.getMessage()
                : "Sign-in with your provider failed. Please try again.";

        log.warn("OAuth2 authentication failed: {}", message);

        String redirectUrl = UriComponentsBuilder.fromUriString(frontendLoginUri)
                .queryParam("oauth2Error",
                        URLEncoder.encode(message, StandardCharsets.UTF_8))
                .build(true)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
