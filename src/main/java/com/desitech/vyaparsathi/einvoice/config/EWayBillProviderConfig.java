package com.desitech.vyaparsathi.einvoice.config;

import com.desitech.vyaparsathi.einvoice.provider.EWayBillProvider;
import com.desitech.vyaparsathi.einvoice.provider.MockEWayBillProvider;
import com.desitech.vyaparsathi.einvoice.provider.NicGspEWayBillProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EWayBillProperties.class)
public class EWayBillProviderConfig {

    private static final Logger logger = LoggerFactory.getLogger(EWayBillProviderConfig.class);

    @Bean
    public EWayBillProvider ewayBillProvider(EWayBillProperties props) {
        String choice = props.getProvider() == null ? "mock" : props.getProvider().trim().toLowerCase();
        EWayBillProvider provider = switch (choice) {
            case "nic" -> new NicGspEWayBillProvider(props);
            case "mock" -> new MockEWayBillProvider();
            default -> {
                logger.warn("Unknown vyaparsathi.eway.provider='{}'. Falling back to 'mock'.", choice);
                yield new MockEWayBillProvider();
            }
        };
        logger.info("E-Way Bill provider: {}", provider.getProviderName());
        return provider;
    }
}
