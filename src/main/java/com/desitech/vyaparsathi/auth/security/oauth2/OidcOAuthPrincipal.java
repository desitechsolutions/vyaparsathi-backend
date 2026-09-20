package com.desitech.vyaparsathi.auth.security.oauth2;

import com.desitech.vyaparsathi.auth.entity.User;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Map;

/**
 * Extends {@link OAuthPrincipal} and implements {@link OidcUser} so it can be
 * returned from {@link OidcOAuthUserService} (which must return an {@link OidcUser})
 * while still satisfying the {@code instanceof OAuthPrincipal} check in
 * {@link OAuth2AuthenticationSuccessHandler}.
 *
 * <p>OIDC-specific methods delegate to the underlying {@link OidcUser} produced
 * by Spring Security's default {@code OidcUserService}.
 */
public class OidcOAuthPrincipal extends OAuthPrincipal implements OidcUser {

    private final OidcUser delegate;

    public OidcOAuthPrincipal(User user, OidcUser delegate) {
        super(user, delegate.getAttributes());
        this.delegate = delegate;
    }

    // ── OidcUser ─────────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }
}
