package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.sales.dto.InvoiceTokenData;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Base64;
import java.util.logging.Logger;

@Component
public class JwtUtil {

    private static final Logger logger = Logger.getLogger(JwtUtil.class.getName());
    @Value("${jwt.invoice.expiration:1800000}") // 30 minutes default
    private long invoiceExpirationMs;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${jwt.refreshExpiration}")
    private long jwtRefreshExpirationMs;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret key is too weak! Must be at least 256 bits.");
        }
    }

    // --- ACCESS TOKEN GENERATION ---
    public String generateAccessToken(User user, Long shopId) {
        JwtBuilder builder = Jwts.builder()
                .setSubject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("firstName", user.getFirstName())
                .claim("lastName", user.getLastName())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs));

        if (shopId != null) {
            builder.claim("shopId", shopId);
        }

        return builder.signWith(secretKey, SignatureAlgorithm.HS512).compact();
    }

    // --- REFRESH TOKEN GENERATION ---
    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtRefreshExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    // --- EXTRACTION METHODS ---
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Long extractShopId(String token) {
        return parseClaims(token).get("shopId", Long.class);
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    // --- VALIDATION ---
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid JWT: " + e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    /**
     * Generates a short-lived JWT specifically for viewing/downloading one invoice.
     * @param saleId the sale identifier
     * @param invoiceNo the invoice number
     * @return signed JWT token (valid for ~30 minutes)
     */
    public String generateInvoiceToken(Long saleId, String invoiceNo) {
        return Jwts.builder()
                .setSubject("invoice-access")  // special subject to identify
                .claim("saleId", saleId)
                .claim("invoiceNo", invoiceNo)
                .claim("scope", "invoice:read")  // optional: add scope for extra security
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + invoiceExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-invoice-service")
                .compact();
    }

    // New method: Validate invoice token and extract saleId/invoiceNo
    public InvoiceTokenData validateInvoiceToken(String token) {
        try {
            Claims claims = parseClaims(token);

            String subject = claims.getSubject();
            if (!"invoice-access".equals(subject)) {
                throw new JwtException("Invalid subject for invoice token");
            }

            String scope = claims.get("scope", String.class);
            if (!"invoice:read".equals(scope)) {
                throw new JwtException("Invalid scope for invoice token");
            }

            Long saleId = claims.get("saleId", Long.class);
            String invoiceNo = claims.get("invoiceNo", String.class);

            if (saleId == null && (invoiceNo == null || invoiceNo.isBlank())) {
                throw new JwtException("Missing saleId or invoiceNo in token");
            }

            return new InvoiceTokenData(saleId, invoiceNo);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid invoice JWT: " + e.getMessage());
            throw e;
        }
    }
}
