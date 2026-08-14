package com.desitech.vyaparsathi.auth.security;

import com.desitech.vyaparsathi.accounting.dto.CreditNoteTokenData;
import com.desitech.vyaparsathi.accounting.dto.DebitNoteTokenData;
import com.desitech.vyaparsathi.auth.entity.User;
import com.desitech.vyaparsathi.delivery.dto.DeliveryChallanTokenData;
import com.desitech.vyaparsathi.invoice.dto.InvoiceTokenData;
import com.desitech.vyaparsathi.purchaseorder.dto.PurchaseOrderTokenData;
import com.desitech.vyaparsathi.quotation.dto.QuotationTokenData;
import com.desitech.vyaparsathi.receipt.dto.ReceiptTokenData;
import com.desitech.vyaparsathi.refund.dto.RefundTokenData;
import com.desitech.vyaparsathi.salesorder.dto.SalesOrderTokenData;
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
    @Value("${jwt.invoice.expiration:1800000}")
    private long invoiceExpirationMs;

    @Value("${jwt.receipt.expiration:1800000}")
    private long receiptExpirationMs;

    @Value("${jwt.note.expiration:1800000}")
    private long noteExpirationMs;

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

    public <T> T extractClaim(String token, java.util.function.Function<Claims, T> claimsResolver) {
        final Claims claims = parseClaims(token);
        return claimsResolver.apply(claims);
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

    /**
     * Generates a short-lived JWT for viewing/downloading one payment receipt.
     * Mirrors {@link #generateInvoiceToken(Long, String)}.
     */
    public String generateReceiptToken(Long receiptId, String receiptNumber) {
        return Jwts.builder()
                .setSubject("receipt-access")
                .claim("receiptId", receiptId)
                .claim("receiptNumber", receiptNumber)
                .claim("scope", "receipt:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + receiptExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-receipt-service")
                .compact();
    }

    public ReceiptTokenData validateReceiptToken(String token) {
        try {
            Claims claims = parseClaims(token);

            String subject = claims.getSubject();
            if (!"receipt-access".equals(subject)) {
                throw new JwtException("Invalid subject for receipt token");
            }

            String scope = claims.get("scope", String.class);
            if (!"receipt:read".equals(scope)) {
                throw new JwtException("Invalid scope for receipt token");
            }

            Long receiptId = claims.get("receiptId", Long.class);
            String receiptNumber = claims.get("receiptNumber", String.class);

            if (receiptId == null && (receiptNumber == null || receiptNumber.isBlank())) {
                throw new JwtException("Missing receiptId or receiptNumber in token");
            }

            return new ReceiptTokenData(receiptId, receiptNumber);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid receipt JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── DELIVERY CHALLAN TOKEN ───────────────────────────────────────────
    public String generateDeliveryChallanToken(Long deliveryId, String challanNo) {
        return Jwts.builder()
                .setSubject("delivery-challan-access")
                .claim("deliveryId", deliveryId)
                .claim("challanNo", challanNo)
                .claim("scope", "delivery-challan:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + invoiceExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-delivery-challan-service")
                .compact();
    }

    public DeliveryChallanTokenData validateDeliveryChallanToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"delivery-challan-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for delivery challan token");
            }
            if (!"delivery-challan:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for delivery challan token");
            }
            Long id = claims.get("deliveryId", Long.class);
            String no = claims.get("challanNo", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing deliveryId or challanNo in token");
            }
            return new DeliveryChallanTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid delivery-challan JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── SALES ORDER TOKEN ────────────────────────────────────────────────
    public String generateSalesOrderToken(Long salesOrderId, String orderNo) {
        return Jwts.builder()
                .setSubject("sales-order-access")
                .claim("salesOrderId", salesOrderId)
                .claim("orderNo", orderNo)
                .claim("scope", "sales-order:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + invoiceExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-sales-order-service")
                .compact();
    }

    public SalesOrderTokenData validateSalesOrderToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"sales-order-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for sales order token");
            }
            if (!"sales-order:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for sales order token");
            }
            Long id = claims.get("salesOrderId", Long.class);
            String no = claims.get("orderNo", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing salesOrderId or orderNo in token");
            }
            return new SalesOrderTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid sales-order JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── QUOTATION TOKEN ──────────────────────────────────────────────────
    public String generateQuotationToken(Long quotationId, String quotationNo) {
        return Jwts.builder()
                .setSubject("quotation-access")
                .claim("quotationId", quotationId)
                .claim("quotationNo", quotationNo)
                .claim("scope", "quotation:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + invoiceExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-quotation-service")
                .compact();
    }

    public QuotationTokenData validateQuotationToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"quotation-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for quotation token");
            }
            if (!"quotation:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for quotation token");
            }
            Long id = claims.get("quotationId", Long.class);
            String no = claims.get("quotationNo", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing quotationId or quotationNo in token");
            }
            return new QuotationTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid quotation JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── PURCHASE ORDER TOKEN ─────────────────────────────────────────────
    // Signed-URL access for the PO PDF endpoint. Mirrors the quotation-token
    // pattern exactly — subject/scope kept distinct so tokens can't be reused
    // across document types.
    public String generatePurchaseOrderToken(Long purchaseOrderId, String poNumber) {
        return Jwts.builder()
                .setSubject("purchase-order-access")
                .claim("purchaseOrderId", purchaseOrderId)
                .claim("poNumber", poNumber)
                .claim("scope", "purchase-order:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + invoiceExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-purchase-order-service")
                .compact();
    }

    public PurchaseOrderTokenData validatePurchaseOrderToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"purchase-order-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for purchase-order token");
            }
            if (!"purchase-order:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for purchase-order token");
            }
            Long id = claims.get("purchaseOrderId", Long.class);
            String no = claims.get("poNumber", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing purchaseOrderId or poNumber in token");
            }
            return new PurchaseOrderTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid purchase-order JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── REFUND TOKEN ─────────────────────────────────────────────────────
    public String generateRefundToken(Long refundId, String refundNo) {
        return Jwts.builder()
                .setSubject("refund-access")
                .claim("refundId", refundId)
                .claim("refundNo", refundNo)
                .claim("scope", "refund:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + receiptExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-refund-service")
                .compact();
    }

    public RefundTokenData validateRefundToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"refund-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for refund token");
            }
            if (!"refund:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for refund token");
            }
            Long id = claims.get("refundId", Long.class);
            String no = claims.get("refundNo", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing refundId or refundNo in token");
            }
            return new RefundTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid refund JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── CREDIT NOTE TOKEN ────────────────────────────────────────────────
    public String generateCreditNoteToken(Long creditNoteId, String creditNoteNo) {
        return Jwts.builder()
                .setSubject("credit-note-access")
                .claim("creditNoteId", creditNoteId)
                .claim("creditNoteNo", creditNoteNo)
                .claim("scope", "credit-note:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + noteExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-credit-note-service")
                .compact();
    }

    public CreditNoteTokenData validateCreditNoteToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"credit-note-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for credit note token");
            }
            if (!"credit-note:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for credit note token");
            }
            Long id = claims.get("creditNoteId", Long.class);
            String no = claims.get("creditNoteNo", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing creditNoteId or creditNoteNo in token");
            }
            return new CreditNoteTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid credit-note JWT: " + e.getMessage());
            throw e;
        }
    }

    // ─── DEBIT NOTE TOKEN ─────────────────────────────────────────────────
    public String generateDebitNoteToken(Long debitNoteId, String debitNoteNo) {
        return Jwts.builder()
                .setSubject("debit-note-access")
                .claim("debitNoteId", debitNoteId)
                .claim("debitNoteNo", debitNoteNo)
                .claim("scope", "debit-note:read")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + noteExpirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .setIssuer("vyaparsathi-debit-note-service")
                .compact();
    }

    public DebitNoteTokenData validateDebitNoteToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"debit-note-access".equals(claims.getSubject())) {
                throw new JwtException("Invalid subject for debit note token");
            }
            if (!"debit-note:read".equals(claims.get("scope", String.class))) {
                throw new JwtException("Invalid scope for debit note token");
            }
            Long id = claims.get("debitNoteId", Long.class);
            String no = claims.get("debitNoteNo", String.class);
            if (id == null && (no == null || no.isBlank())) {
                throw new JwtException("Missing debitNoteId or debitNoteNo in token");
            }
            return new DebitNoteTokenData(id, no);
        } catch (JwtException | IllegalArgumentException e) {
            logger.warning("Invalid debit-note JWT: " + e.getMessage());
            throw e;
        }
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

    // --- IMPERSONATION TOKEN GENERATION (15-Min TTL) ---
    public String generateImpersonationToken(User targetUser, Long shopId, String sessionUuid, Long superAdminId, String superAdminEmail) {
        return Jwts.builder()
                .setSubject(targetUser.getUsername())
                .claim("role", targetUser.getRole().name())
                .claim("firstName", targetUser.getFirstName())
                .claim("lastName", targetUser.getLastName())
                .claim("shopId", shopId)
                .claim("isImpersonation", true)
                .claim("impersonationSessionId", sessionUuid)
                .claim("originalAdminId", superAdminId)
                .claim("originalAdminEmail", superAdminEmail)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 15 * 60 * 1000L)) // 15 minutes TTL
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Boolean isImpersonationToken(String token) {
        try {
            Boolean flag = parseClaims(token).get("isImpersonation", Boolean.class);
            return Boolean.TRUE.equals(flag);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractImpersonationSessionId(String token) {
        try {
            return parseClaims(token).get("impersonationSessionId", String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
