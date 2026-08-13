package com.desitech.vyaparsathi.einvoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for E-Way Bill provider selection + credentials.
 *
 * Example application.properties:
 * <pre>
 * vyaparsathi.eway.provider=mock
 * vyaparsathi.eway.nic.api-url=https://gsp.example.com/ewaybill
 * vyaparsathi.eway.nic.username=${EWB_NIC_USER}
 * vyaparsathi.eway.nic.password=${EWB_NIC_PASS}
 * vyaparsathi.eway.nic.gstin=27ABCDE1234F1Z5
 * vyaparsathi.eway.nic.client-id=${EWB_CLIENT_ID}
 * vyaparsathi.eway.nic.client-secret=${EWB_CLIENT_SECRET}
 * </pre>
 *
 * Store credentials in GCP Secret Manager and inject via {@code ${…}}. Do NOT
 * commit real NIC / GSP credentials — they grant EWB-generation authority on
 * the shop's GSTIN.
 */
@ConfigurationProperties(prefix = "vyaparsathi.eway")
public class EWayBillProperties {

    /** "mock" (default) or "nic". */
    private String provider = "mock";

    private Nic nic = new Nic();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public Nic getNic() { return nic; }
    public void setNic(Nic nic) { this.nic = nic; }

    public static class Nic {
        private String apiUrl;
        private String username;
        private String password;
        private String gstin;
        private String clientId;
        private String clientSecret;
        /** HTTP timeout in seconds for GSP calls. Default 15s. */
        private int timeoutSeconds = 15;

        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getGstin() { return gstin; }
        public void setGstin(String gstin) { this.gstin = gstin; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
