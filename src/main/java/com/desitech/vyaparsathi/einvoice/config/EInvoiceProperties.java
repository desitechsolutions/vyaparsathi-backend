package com.desitech.vyaparsathi.einvoice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime configuration for the e-invoice subsystem.
 *
 * Default: {@code provider=MOCK} — the app boots and generates fake IRNs
 * without any external dependency. Shops flip to {@code NIC} in prod when
 * they have credentials issued by their GSP (GST Suvidha Provider) or
 * direct API access from the NIC portal.
 *
 * NIC issues credentials per-GSTIN. In a multi-tenant SaaS this ideally
 * lives at the Shop level (each shop's own client-id/secret). For the
 * initial cut we keep credentials global — a follow-up can move them
 * onto the Shop entity so each tenant configures their own.
 */
@Component
@ConfigurationProperties(prefix = "einvoice")
public class EInvoiceProperties {

    public enum Provider { MOCK, NIC }

    private Provider provider = Provider.MOCK;
    private Nic nic = new Nic();

    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }

    public Nic getNic() { return nic; }
    public void setNic(Nic nic) { this.nic = nic; }

    public static class Nic {
        /** Sandbox: https://einv-apisandbox.nic.in. Production: https://einvoice1.gst.gov.in. */
        private String baseUrl = "https://einv-apisandbox.nic.in";

        /** Client credentials issued by NIC. Blank in dev — MOCK will be used. */
        private String clientId = "";
        private String clientSecret = "";

        /** GSTIN username / password issued by NIC for each registered taxpayer. */
        private String username = "";
        private String password = "";

        /** True when hitting sandbox — enables extra debug logging and looser retry policy. */
        private boolean sandbox = true;

        /** Request timeout in ms. NIC's SLA is 3s p99; 10s is a safe ceiling. */
        private int timeoutMs = 10_000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public boolean isSandbox() { return sandbox; }
        public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }

        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    }
}
