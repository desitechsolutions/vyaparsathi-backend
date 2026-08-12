package com.desitech.vyaparsathi.einvoice.nic;

import com.desitech.vyaparsathi.einvoice.config.EInvoiceProperties;
import com.desitech.vyaparsathi.einvoice.provider.EInvoiceProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auth-token cache for the NIC IRP.
 *
 * NIC issues a per-GSTIN auth token via {@code POST /eivital/v1.04/auth}. The
 * token is valid for 6 hours in sandbox and typically 30 minutes in prod. We
 * cache aggressively with a 25-minute TTL — well under the prod validity so
 * a rolling refresh never races the expiry — and re-issue on 401/403 responses
 * from downstream calls.
 *
 * Real NIC auth involves AES/RSA encryption with a session key derived from
 * NIC's public key. This scaffold performs the plain HTTP round-trip and
 * leaves the encryption step as a clearly-marked TODO — callers running
 * against the real IRP will need to plug in the encryption module (typically
 * via the {@code bcprov-jdk18on} library which is already common in this
 * space). Sandbox accepts unencrypted payloads for smoke testing.
 */
@Service
@ConditionalOnProperty(prefix = "einvoice", name = "provider", havingValue = "NIC")
public class NicAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(NicAuthTokenService.class);
    /** NIC tokens are typically 30min in prod, 6h in sandbox. 25min is safe for both. */
    private static final long TTL_MILLIS = 25L * 60L * 1000L;
    /** Refresh a bit before the TTL wall-clock so concurrent calls never race a stale token. */
    private static final long REFRESH_SLACK_MILLIS = 2L * 60L * 1000L;

    private final EInvoiceProperties props;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    private final ConcurrentHashMap<String, CachedToken> tokensByGstin = new ConcurrentHashMap<>();

    public NicAuthTokenService(EInvoiceProperties props, RestTemplate restTemplate) {
        this.props = props;
        this.restTemplate = restTemplate;
    }

    /**
     * Return a valid auth token for the given GSTIN, refreshing lazily if
     * the cache entry is expired or near expiry.
     */
    public String getToken(String gstin) {
        if (gstin == null || gstin.isBlank()) {
            throw new EInvoiceProviderException("GSTIN required to fetch NIC auth token");
        }
        CachedToken cached = tokensByGstin.get(gstin);
        long now = Instant.now().toEpochMilli();
        if (cached != null && (cached.expiresAt - now) > REFRESH_SLACK_MILLIS) {
            return cached.value;
        }
        return refresh(gstin);
    }

    /** Force a refresh — typically called after a 401/403 from an IRP call. */
    public String refresh(String gstin) {
        String token = fetchToken(gstin);
        long expiresAt = Instant.now().toEpochMilli() + TTL_MILLIS;
        tokensByGstin.put(gstin, new CachedToken(token, expiresAt));
        return token;
    }

    public void invalidate(String gstin) {
        tokensByGstin.remove(gstin);
    }

    // ── HTTP round-trip ────────────────────────────────────────

    private String fetchToken(String gstin) {
        String url = props.getNic().getBaseUrl() + "/eivital/v1.04/auth";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("client_id", props.getNic().getClientId());
        headers.set("client_secret", props.getNic().getClientSecret());
        headers.set("gstin", gstin);

        // TODO(nic-encryption): real IRP requires AES-encrypted payload with a
        //   session key wrapped by NIC's RSA public key. Sandbox accepts plain
        //   JSON — the wrapper below is what the encryption step would produce.
        Map<String, Object> body = Map.of(
                "UserName", props.getNic().getUsername(),
                "Password", props.getNic().getPassword(),
                "AppKey",   generateAppKey(),
                "ForceRefreshAccessToken", true
        );

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = mapper.readTree(response.getBody());
            // NIC returns { "Status": 1, "Data": { "AuthToken": "...", "Sek": "..." } }
            String status = root.path("Status").asText("");
            if (!"1".equals(status)) {
                String err = root.path("ErrorDetails").toString();
                throw new EInvoiceProviderException("NIC auth failed: " + err, root.path("ErrorCode").asText(null));
            }
            String token = root.path("Data").path("AuthToken").asText(null);
            if (token == null || token.isBlank()) {
                throw new EInvoiceProviderException("NIC auth response missing AuthToken");
            }
            log.info("NIC auth token refreshed for GSTIN={}", mask(gstin));
            return token;
        } catch (RestClientException e) {
            throw new EInvoiceProviderException("NIC auth transport error: " + e.getMessage(), null, e);
        } catch (Exception e) {
            if (e instanceof EInvoiceProviderException epe) throw epe;
            throw new EInvoiceProviderException("NIC auth parse error: " + e.getMessage(), null, e);
        }
    }

    /** 32-char session key. NIC requires an app-generated random string. */
    private String generateAppKey() {
        return java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String mask(String gstin) {
        if (gstin == null || gstin.length() < 4) return "****";
        return gstin.substring(0, 2) + "****" + gstin.substring(gstin.length() - 4);
    }

    private static final class CachedToken {
        final String value;
        final long expiresAt;
        CachedToken(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }
}
