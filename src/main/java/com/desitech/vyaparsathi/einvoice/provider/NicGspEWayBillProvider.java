package com.desitech.vyaparsathi.einvoice.provider;

import com.desitech.vyaparsathi.einvoice.config.EWayBillProperties;
import com.desitech.vyaparsathi.einvoice.dto.EWayBillRequestDto;
import com.desitech.vyaparsathi.sales.entity.Sale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real GSP-authorized NIC E-Way Bill provider — stub. To wire this up:
 *
 * <ol>
 *   <li>Sign a contract with a GSP (Cygnet, ClearTax, Masters India, etc.);
 *       they proxy the NIC EWB portal for us.</li>
 *   <li>Fill in {@code vyaparsathi.eway.nic.*} in application.properties or
 *       Secret Manager: {@code apiUrl}, {@code username}, {@code password},
 *       {@code gstin}, {@code clientId}, {@code clientSecret}.</li>
 *   <li>Set {@code vyaparsathi.eway.provider=nic}.</li>
 *   <li>Implement {@link #generate} — build the request per the GSP's spec
 *       (usually POST /ewb/generate with an Auth-Token header from a prior
 *       login call), map the response to {@link Result}. Add retries + a
 *       15-second timeout.</li>
 *   <li>Cache the Auth-Token (typically 6-hour TTL) — a new one each call
 *       will trigger rate-limit / lockout on the NIC side.</li>
 * </ol>
 *
 * Until this is done, calling this provider throws so we fail loud rather
 * than silently minting a fake number that won't validate against the
 * portal.
 */
public class NicGspEWayBillProvider implements EWayBillProvider {

    private static final Logger logger = LoggerFactory.getLogger(NicGspEWayBillProvider.class);
    private final EWayBillProperties props;

    public NicGspEWayBillProvider(EWayBillProperties props) {
        this.props = props;
    }

    @Override
    public Result generate(Sale sale, EWayBillRequestDto request) {
        // TODO(gsp): wire up real GSP call. See class-level JavaDoc for the checklist.
        logger.error("NIC GSP provider selected but not yet implemented — check " +
                "vyaparsathi.eway.provider config. GSTIN configured: {}",
                props.getNic() != null ? props.getNic().getGstin() : "(none)");
        throw new EWayBillProviderException(getProviderName(),
                "Real NIC GSP integration is not wired up yet. Set " +
                        "vyaparsathi.eway.provider=mock to use the synthetic provider, or " +
                        "implement NicGspEWayBillProvider.generate().");
    }

    @Override
    public String getProviderName() {
        return "nic";
    }
}
