package com.desitech.vyaparsathi.einvoice.provider;

import com.desitech.vyaparsathi.einvoice.config.EInvoiceProperties;
import com.desitech.vyaparsathi.einvoice.nic.EInvoicePayloadBuilder;
import com.desitech.vyaparsathi.einvoice.nic.NicAuthTokenService;
import com.desitech.vyaparsathi.sales.entity.Sale;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Real IRP provider — talks to NIC via HTTPS.
 *
 * Endpoints (Schema 1.03/1.04):
 *   POST /eicore/v1.03/Invoice          — generate IRN
 *   POST /eicore/v1.03/Invoice/Cancel   — cancel IRN
 *   POST /eivital/v1.04/auth            — auth token (handled by NicAuthTokenService)
 *
 * Selected when {@code einvoice.provider=NIC}. If credentials are unset,
 * boot will still succeed but the first call throws — shops that flip the
 * property must configure credentials at the same time.
 *
 * The real NIC IRP requires AES-encrypted request bodies with a session key
 * derived from NIC's public key. This scaffold sends plaintext JSON — which
 * works against the sandbox but will be rejected in production. See the
 * TODO in {@code NicAuthTokenService} for the encryption plug-in point.
 */
@Component
@ConditionalOnProperty(prefix = "einvoice", name = "provider", havingValue = "NIC")
public class NicEInvoiceProvider implements EInvoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(NicEInvoiceProvider.class);
    private static final String GENERATE_PATH = "/eicore/v1.03/Invoice";
    private static final String CANCEL_PATH   = "/eicore/v1.03/Invoice/Cancel";
    private static final DateTimeFormatter ACK_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EInvoiceProperties props;
    private final EInvoicePayloadBuilder payloadBuilder;
    private final NicAuthTokenService authService;
    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public NicEInvoiceProvider(EInvoiceProperties props,
                               EInvoicePayloadBuilder payloadBuilder,
                               NicAuthTokenService authService,
                               RestTemplate restTemplate) {
        this.props = props;
        this.payloadBuilder = payloadBuilder;
        this.authService = authService;
        this.restTemplate = restTemplate;
    }

    @Override
    public GenerateResult generateIrn(Sale sale) {
        String gstin = requireShopGstin(sale);
        Map<String, Object> payload = payloadBuilder.build(sale);

        JsonNode data = callNic(gstin, GENERATE_PATH, payload, "generate IRN");
        String irn = data.path("Irn").asText(null);
        String ackNo = data.path("AckNo").asText(null);
        String ackDtRaw = data.path("AckDt").asText(null);
        String signedInvoice = data.path("SignedInvoice").asText(null);
        String signedQrCode = data.path("SignedQRCode").asText(null);
        String qrCodeUrl = data.path("QRCodeUrl").asText(null);

        if (irn == null || irn.isBlank()) {
            throw new EInvoiceProviderException("NIC IRN response missing Irn field");
        }
        LocalDateTime ackDate = parseAckDate(ackDtRaw);
        log.info("NIC IRN generated invoice={} irn={}", sale.getInvoiceNo(), maskIrn(irn));
        return new GenerateResult(irn, ackNo, ackDate, signedInvoice, signedQrCode, qrCodeUrl);
    }

    @Override
    public CancelResult cancelIrn(Sale sale, String cancelReason) {
        String gstin = requireShopGstin(sale);
        Map<String, Object> payload = Map.of(
                "Irn", sale.getIrn(),
                "CnlRsn", cancelReason == null ? "1" : cancelReason,
                "CnlRem", "Cancelled via portal"
        );
        JsonNode data = callNic(gstin, CANCEL_PATH, payload, "cancel IRN");
        String cancelDtRaw = data.path("CancelDate").asText(null);
        LocalDateTime cancelDate = parseAckDate(cancelDtRaw);
        return new CancelResult(sale.getIrn(), cancelDate != null ? cancelDate : LocalDateTime.now());
    }

    @Override public String getProviderName() { return props.getNic().isSandbox() ? "NIC_SANDBOX" : "NIC_PRODUCTION"; }
    @Override public boolean isLive() { return !props.getNic().isSandbox(); }

    // ── HTTP round-trip ────────────────────────────────────────

    private JsonNode callNic(String gstin, String path, Object body, String opLabel) {
        String url = props.getNic().getBaseUrl() + path;
        String authToken = authService.getToken(gstin);

        HttpHeaders headers = buildHeaders(gstin, authToken);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            // 401/403 typically means token expired between our TTL and NIC's clock —
            // refresh once and retry.
            if (status.value() == 401 || status.value() == 403) {
                authService.invalidate(gstin);
                authToken = authService.refresh(gstin);
                try {
                    headers = buildHeaders(gstin, authToken);
                    response = restTemplate.exchange(
                            url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
                } catch (RestClientException retry) {
                    throw new EInvoiceProviderException(
                            "NIC " + opLabel + " retry failed: " + retry.getMessage(), null, retry);
                }
            } else {
                throw new EInvoiceProviderException(
                        "NIC " + opLabel + " failed (" + status + "): " + e.getResponseBodyAsString(),
                        String.valueOf(status.value()), e);
            }
        } catch (RestClientException e) {
            throw new EInvoiceProviderException("NIC " + opLabel + " transport error: " + e.getMessage(), null, e);
        }

        try {
            JsonNode root = mapper.readTree(response.getBody());
            String status = root.path("Status").asText("");
            if (!"1".equals(status)) {
                String errCode = root.path("ErrorDetails").path(0).path("ErrorCode").asText(null);
                String errMsg = root.path("ErrorDetails").toString();
                throw new EInvoiceProviderException(
                        "NIC " + opLabel + " rejected: " + errMsg, errCode);
            }
            return root.path("Data");
        } catch (EInvoiceProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new EInvoiceProviderException("NIC " + opLabel + " parse error: " + e.getMessage(), null, e);
        }
    }

    private HttpHeaders buildHeaders(String gstin, String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("client_id", props.getNic().getClientId());
        headers.set("client_secret", props.getNic().getClientSecret());
        headers.set("gstin", gstin);
        headers.set("user_name", props.getNic().getUsername());
        headers.set("AuthToken", authToken);
        return headers;
    }

    private String requireShopGstin(Sale sale) {
        String gstin = sale.getShop() != null ? sale.getShop().getGstin() : null;
        if (gstin == null || gstin.isBlank()) {
            throw new EInvoiceProviderException(
                    "Shop GSTIN is required for NIC e-invoicing (sale " + sale.getId() + ")");
        }
        return gstin;
    }

    private static LocalDateTime parseAckDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalDateTime.parse(raw, ACK_FMT); }
        catch (Exception e) { return null; }
    }

    private static String maskIrn(String irn) {
        if (irn == null || irn.length() < 12) return "****";
        return irn.substring(0, 8) + "..." + irn.substring(irn.length() - 4);
    }
}
