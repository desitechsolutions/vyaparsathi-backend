package com.desitech.vyaparsathi.auth.security.oauth2;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.auth.model.AuthProvider;
import com.desitech.vyaparsathi.auth.model.Role;
import com.desitech.vyaparsathi.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * OIDC-specific user service for providers that use the {@code openid} scope
 * (e.g. Google).
 *
 * <p>Spring Security routes OIDC providers through {@link OidcUserService}
 * rather than {@link OAuthUserService}, so without this class the principal
 * returned is a plain {@link org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser}
 * which cannot be cast to {@link OAuthPrincipal} in the success handler.
 *
 * <p>This service replicates the same find-or-create logic as
 * {@link OAuthUserService} and always returns an {@link OAuthPrincipal}
 * so the success handler receives a consistent type regardless of OAuth2 vs OIDC flow.
 */
@Service
@RequiredArgsConstructor
public class OidcOAuthUserService extends OidcUserService {

    private static final Logger log = LoggerFactory.getLogger(OidcOAuthUserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        // Let the default OIDC service do the token exchange and build the OidcUser
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        AuthProvider provider = resolveProvider(registrationId);

        String email      = oidcUser.getEmail();
        String providerId = oidcUser.getSubject();          // OIDC "sub" claim
        String firstName  = oidcUser.getGivenName();
        String lastName   = oidcUser.getFamilyName();

        // Fallback: split "name" if given_name / family_name are absent
        if (firstName == null) {
            String name = oidcUser.getFullName();
            if (name != null && name.contains(" ")) {
                firstName = name.substring(0, name.indexOf(' '));
                if (lastName == null) lastName = name.substring(name.lastIndexOf(' ') + 1);
            } else {
                firstName = name;
            }
        }

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_email"),
                    "Your " + registrationId + " account does not share an email address. " +
                    "Please grant email access in your provider settings and try again.");
        }

        User user = findOrCreate(provider, providerId, email, firstName, lastName);
        // OidcOAuthPrincipal extends OAuthPrincipal (→ success handler instanceof check passes)
        // AND implements OidcUser (→ satisfies OidcUserService return type contract).
        return new OidcOAuthPrincipal(user, oidcUser);
    }

    // ── Account resolution ───────────────────────────────────────────────────

    private User findOrCreate(AuthProvider provider, String providerId,
                              String email, String firstName, String lastName) {
        // 1. Exact provider + id match
        Optional<User> byProvider = userRepository.findByAuthProviderAndAuthProviderId(provider, providerId);
        if (byProvider.isPresent()) {
            User u = byProvider.get();
            updateNameIfChanged(u, firstName, lastName);
            return userRepository.save(u);
        }

        // 2. Same email → link provider to existing account
        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User u = byEmail.get();
            log.info("Linking {} provider to existing account for email {}", provider, email);
            u.setAuthProvider(provider);
            u.setAuthProviderId(providerId);
            u.setEmailVerified(true);
            updateNameIfChanged(u, firstName, lastName);
            return userRepository.save(u);
        }

        // 3. New user
        log.info("Creating new user from {} OIDC login: {}", provider, email);
        User newUser = new User();
        newUser.setUsername(email);
        newUser.setEmail(email);
        newUser.setFirstName(firstName);
        newUser.setLastName(lastName);
        newUser.setRole(Role.PENDING_OWNER); // promoted to OWNER when shop setup is completed
        newUser.setActive(true);
        newUser.setEmailVerified(true);
        newUser.setAuthProvider(provider);
        newUser.setAuthProviderId(providerId);
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
            log.debug("Updated name for OIDC user {}", user.getUsername());
        }
    }

    private AuthProvider resolveProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google"                          -> AuthProvider.GOOGLE;
            case "microsoft", "azure", "azuread"   -> AuthProvider.MICROSOFT;
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("unsupported_provider"),
                    "Unsupported OAuth2 provider: " + registrationId);
        };
    }
}
