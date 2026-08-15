package com.desitech.vyaparsathi.common.configs;

import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import org.springframework.boot.jackson.JsonComponent;

/**
 * Registers Jackson's Hibernate6 module so lazy-loaded proxies serialize as
 * either the loaded value or {@code null} (never as a ByteBuddy proxy class).
 * Fixes the crash observed when returning entities with lazy {@code @ManyToOne}
 * fields (e.g. ShopAwareEntity.shop) from REST endpoints.
 */
@JsonComponent
public class JacksonHibernateConfig extends Hibernate6Module {
    public JacksonHibernateConfig() {
        // Serialize identifiers only for non-initialized proxies — keeps the
        // payload lean and avoids N+1 selects triggered by force-load.
        disable(Feature.USE_TRANSIENT_ANNOTATION);
        enable(Feature.SERIALIZE_IDENTIFIER_FOR_LAZY_NOT_LOADED_OBJECTS);
    }
}
