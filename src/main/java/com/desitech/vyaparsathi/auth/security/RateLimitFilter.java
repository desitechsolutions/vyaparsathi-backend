package com.desitech.vyaparsathi.auth.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate-limits sensitive auth endpoints to blunt online brute-force and
 * credential-stuffing attacks. Two independent buckets guard each request:
 *
 *   - IP bucket   : 20 requests / 15 min per remote address
 *   - Identity    : 10 attempts / hour per submitted username or email
 *
 * The identity bucket is only consumed on endpoints that name a user
 * (login, forgot-password, reset-password, register). All other auth
 * traffic is IP-limited only. Buckets live in-memory; they are lost on
 * restart which is fine for an online attacker (the DB-backed lockout in
 * {@link com.desitech.vyaparsathi.auth.service.AuthService} is the durable
 * defense).
 *
 * When rate-limited, we return 429 with a {@code Retry-After} header and a
 * JSON body — no user-existence oracle is exposed.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // Auth endpoints that share the strict login/register IP bucket
    private static final Set<String> AUTH_RATE_LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/reset-password",
            "/api/auth/validate-reset-token",
            "/api/auth/refresh",
            "/api/auth/change-pin",
            "/api/auth/change-password",
            "/api/auth/verify-email",
            "/api/auth/mfa/verify",
            "/api/auth/mfa/setup/init",
            "/api/auth/mfa/setup/confirm",
            "/api/auth/mfa/regenerate-codes",
            "/api/auth/mfa/disable",
            "/api/sales/offline-queue"
    );

    // Recovery endpoints get their own lenient IP bucket so that hammering
    // login never blocks a legitimate user from resetting their password.
    private static final Set<String> RECOVERY_PATHS = Set.of(
            "/api/auth/forget-password",
            "/api/auth/resend-verification"
    );

    private static final Set<String> IDENTITY_BOUND_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/forget-password",
            "/api/auth/resend-verification"
    );

    // Strict: 20 attempts per 15 minutes per IP for login/register flows
    private static final Bandwidth IP_LIMIT = Bandwidth.builder()
            .capacity(20)
            .refillIntervally(20, Duration.ofMinutes(15))
            .build();

    // Lenient: 10 attempts per 10 minutes per IP for password-recovery flows
    private static final Bandwidth RECOVERY_IP_LIMIT = Bandwidth.builder()
            .capacity(10)
            .refillIntervally(10, Duration.ofMinutes(10))
            .build();

    private static final Bandwidth IDENTITY_LIMIT = Bandwidth.builder()
            .capacity(10)
            .refillIntervally(10, Duration.ofHours(1))
            .build();

    private final ConcurrentMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> recoveryIpBuckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> identityBuckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean isAuth     = AUTH_RATE_LIMITED_PATHS.contains(path);
        boolean isRecovery = RECOVERY_PATHS.contains(path);

        if (!isAuth && !isRecovery) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        if (isAuth) {
            Bucket ipBucket = ipBuckets.computeIfAbsent(clientIp,
                    k -> Bucket.builder().addLimit(IP_LIMIT).build());
            ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
            if (!ipProbe.isConsumed()) {
                reject(response, ipProbe.getNanosToWaitForRefill(),
                        "Too many requests from this network. Try again later.");
                log.warn("Rate limit hit (IP/auth) for {} on {}", clientIp, path);
                return;
            }
        }

        if (isRecovery) {
            // Recovery paths use a separate, more lenient IP bucket so that an
            // exhausted login bucket never blocks a legitimate password-reset attempt.
            Bucket recoveryBucket = recoveryIpBuckets.computeIfAbsent(clientIp,
                    k -> Bucket.builder().addLimit(RECOVERY_IP_LIMIT).build());
            ConsumptionProbe recoveryProbe = recoveryBucket.tryConsumeAndReturnRemaining(1);
            if (!recoveryProbe.isConsumed()) {
                reject(response, recoveryProbe.getNanosToWaitForRefill(),
                        "Too many password reset requests from this network. Try again later.");
                log.warn("Rate limit hit (IP/recovery) for {} on {}", clientIp, path);
                return;
            }
        }

        // For endpoints that carry a username/email, consume the identity bucket too.
        // We read the request body once (cached) so downstream can still deserialize it.
        if (IDENTITY_BOUND_PATHS.contains(path)) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            String identity = extractIdentity(cached, path);
            if (identity != null && !identity.isBlank()) {
                Bucket idBucket = identityBuckets.computeIfAbsent(identity.toLowerCase(),
                        k -> Bucket.builder().addLimit(IDENTITY_LIMIT).build());
                ConsumptionProbe idProbe = idBucket.tryConsumeAndReturnRemaining(1);
                if (!idProbe.isConsumed()) {
                    reject(response, idProbe.getNanosToWaitForRefill(),
                            "Too many attempts for this account. Try again later.");
                    log.warn("Rate limit hit (identity) for identity={} on {}",
                            maskIdentity(identity), path);
                    return;
                }
            }
            filterChain.doFilter(cached, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractIdentity(CachedBodyHttpServletRequest request, String path) {
        try {
            byte[] body = request.getCachedBody();
            if (body == null || body.length == 0) return null;
            JsonNode root = objectMapper.readTree(new ByteArrayInputStream(body));
            if (path.endsWith("/login") || path.endsWith("/register")) {
                JsonNode u = root.get("username");
                return u == null ? null : u.asText();
            }
            if (path.endsWith("/forget-password") || path.endsWith("/resend-verification")) {
                JsonNode e = root.get("email");
                return e == null ? null : e.asText();
            }
        } catch (Exception ex) {
            // Malformed body — let downstream validation return the appropriate 400.
            log.debug("Could not parse identity from request body for {}: {}", path, ex.getMessage());
        }
        return null;
    }

    private void reject(HttpServletResponse response, long nanosToWait, String message) throws IOException {
        long secondsToWait = Math.max(1, Duration.ofNanos(nanosToWait).getSeconds());
        response.setStatus(429); // 429 Too Many Requests
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(secondsToWait));
        String body = String.format(
                "{\"status\":\"error\",\"message\":\"%s\",\"retryAfterSeconds\":%d}",
                message, secondsToWait);
        response.getWriter().write(body);
    }

    private String maskIdentity(String identity) {
        if (identity == null || identity.length() <= 3) return "***";
        return identity.substring(0, 2) + "***" + identity.substring(identity.length() - 1);
    }

    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "CF-Connecting-IP", "True-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Request wrapper that caches the body so we can peek at the JSON to
     * pull the username/email for identity-based rate limiting, then hand
     * the same body to the next filter.
     */
    private static class CachedBodyHttpServletRequest
            extends jakarta.servlet.http.HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        byte[] getCachedBody() { return cachedBody; }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            ByteArrayInputStream buf = new ByteArrayInputStream(cachedBody);
            return new jakarta.servlet.ServletInputStream() {
                @Override public boolean isFinished() { return buf.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(jakarta.servlet.ReadListener readListener) { }
                @Override public int read() { return buf.read(); }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
