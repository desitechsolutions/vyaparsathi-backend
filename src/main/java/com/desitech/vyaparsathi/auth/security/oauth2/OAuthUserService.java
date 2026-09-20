package com.desitech.vyaparsathi.auth.security.oauth2;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.AuthProvider;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Loads our {@link User} during an OAuth2 login and wraps it in a custom
 * {@link OAuthPrincipal} that Spring Security can carry through the rest of
 * the authentication chain.
 *
 * <p>Account linking rule:
 * <ol>
 *   <li>Exact provider match ({@code authProvider + authProviderId}) → same user, log in.
 *   <li>Same email, different / no provider → link the new provider to the existing account.
 *   <li>No match → create a new user. The user will need to complete shop setup.
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class OAuthUserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(OAuthUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = resolveProvider(registrationId);
        Map<String, Object> attrs = oauthUser.getAttributes();

        String email      = extractEmail(provider, attrs);
        String providerId = extractProviderId(provider, attrs);
        String firstName  = extractFirstName(attrs);
        String lastName   = extractLastName(attrs);

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email"),
                    "Your " + registrationId + " account does not share an email address. " +
                    "Please grant email access in your provider settings and try again.");
        }

        User user = findOrCreate(provider, providerId, email, firstName, lastName);
        return new OAuthPrincipal(user, attrs);
    }

    private User findOrCreate(AuthProvider provider, String providerId,
                              String email, String firstName, String lastName) {
        // 1. Exact provider+id match
        Optional<User> byProvider = userRepository.findByAuthProviderAndAuthProviderId(provider, providerId);
        if (byProvider.isPresent()) {
            User u = byProvider.get();
            // Refresh name in case it changed in the provider
            updateNameIfChanged(u, firstName, lastName);
            return userRepository.save(u);
        }

        // 2. Same email → link the provider to the existing account
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            log.info("Linking {} provider to existing account for email {}", provider, email);
            u.setAuthProvider(provider);
            u.setAuthProviderId(providerId);
            u.setEmailVerified(true);   // provider already verified the email
            updateNameIfChanged(u, firstName, lastName);
            return userRepository.save(u);
        }

        // 3. New user
        log.info("Creating new user from {} OAuth2 login: {}", provider, email);
        User newUser = new User();
        newUser.setUsername(email);        // email is unique, works as username
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setRole(Role.PENDING_OWNER); // promoted to OWNER when shop setup is completed
        newUser.setActive(true);
        newUser.setEmailVerified(true);    // provider already verified
        newUser.setAuthProvider(provider);
        newUser.setAuthProviderId(providerId);
        // Random BCrypt hash — no plaintext password exists for this account.
        newUser.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        return userRepository.save(newUser);
    }

    private void updateNameIfChanged(User user, String firstName, String lastName) {
        boolean changed = false;
        if (firstName != null && !firstName.equals(user.getFirstName())) {
            user.setFirstName(firstName);
            changed = true;
        }
        if (lastName != null && !lastName.equals(user.getLastName())) {
            user.setLastName(lastName);
            changed = true;
        }
        if (changed) {
            log.debug("Updated name for OAuth2 user {}", user.getUsername());
        }
    }

    // ── Attribute extraction ──────────────────────────────────────────────────

    private AuthProvider resolveProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google"    -> AuthProvider.GOOGLE;
            case "microsoft", "azure", "azuread" -> AuthProvider.MICROSOFT;
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "Unsupported OAuth2 provider: " + registrationId);
        };
    }

    private String extractEmail(AuthProvider provider, Map<String, Object> attrs) {
        // Google: "email"
        // Microsoft: "email" or "preferred_username" or "upn"
        String email = (String) attrs.get("email");
        if (email == null || email.isBlank()) {
            email = (String) attrs.get("preferred_username");
        }
        if (email == null || email.isBlank()) {
            email = (String) attrs.get("upn");
        }
        return email;
    }

    private String extractProviderId(AuthProvider provider, Map<String, Object> attrs) {
        // Google: "sub"  |  Microsoft: "oid" (stable across tenants)
        String id = provider == AuthProvider.GOOGLE
                ? (String) attrs.get("sub")
                : (String) attrs.getOrDefault("oid", attrs.get("sub"));
        return id != null ? id : String.valueOf(attrs.get("id"));
    }

    private String extractFirstName(Map<String, Object> attrs) {
        String v = (String) attrs.get("given_name");
        if (v != null) return v;
        // Fall back to first word of "name"
        String name = (String) attrs.get("name");
        if (name != null && name.contains(" ")) return name.substring(0, name.indexOf(' '));
        return name;
    }

    private String extractLastName(Map<String, Object> attrs) {
        String v = (String) attrs.get("family_name");
        if (v != null) return v;
        // Fall back to last word of "name"
        String name = (String) attrs.get("name");
        if (name != null && name.contains(" ")) return name.substring(name.lastIndexOf(' ') + 1);
        return null;
    }
}
