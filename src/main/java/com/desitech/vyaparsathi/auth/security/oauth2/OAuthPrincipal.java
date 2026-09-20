package com.desitech.vyaparsathi.auth.security.oauth2;

import com.desitech.vyaparsathi.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Spring Security {@link OAuth2User} wrapper around our {@link User} entity.
 * Carried through the filter chain; the success handler extracts the entity
 * from it to issue our JWT.
 */
public class OAuthPrincipal implements OAuth2User {

    private final User user;
    private final Map<String, Object> attributes;

    public OAuthPrincipal(User user, Map<String, Object> attributes) {
        this.user = user;
        this.attributes = attributes;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getName() {
        return user.getUsername();
    }
}
