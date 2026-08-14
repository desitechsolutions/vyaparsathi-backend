package com.desitech.vyaparsathi.shop.customfield.entity;

import com.desitech.vyaparsathi.common.entities.ShopAwareEntity;
import com.desitech.vyaparsathi.common.jpa.JsonListConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Per-shop custom field definition. Rendered on the variant form by
 * {@code IndustrySlot} alongside the industry-built-in fields.
 *
 * <p>The value for each definition lives on
 * {@link com.desitech.vyaparsathi.inventory.entity.ItemVariant#getCustomAttributes()}
 * as a JSON map keyed by {@link #keyName}. Adding or removing a
 * definition is metadata-only — no schema migration.
 */
@Entity
@Table(name = "shop_custom_attribute_def",
        uniqueConstraints = @UniqueConstraint(name = "uk_shop_custom_attr_key",
                columnNames = {"shop_id", "key_name"}))
@Getter
@Setter
@NoArgsConstructor
public class ShopCustomAttributeDef extends ShopAwareEntity {

    /** Machine-readable JSON key, e.g. {@code "returnWindowDays"}. */
    @Column(name = "key_name", nullable = false, length = 64)
    private String keyName;

    /** Human-readable label shown on the form, e.g. "Return Window (days)". */
    @Column(nullable = false, length = 120)
    private String label;

    /**
     * Widget type: {@code text | number | date | select | boolean}.
     * Kept as a string to avoid a global enum change when a new widget
     * is added.
     */
    @Column(name = "field_type", nullable = false, length = 20)
    private String fieldType;

    @Column(nullable = false)
    private Boolean required = Boolean.FALSE;

    /** For {@code select} fields — the allowed values. Ignored otherwise. */
    @Convert(converter = JsonListConverter.class)
    @Column(columnDefinition = "JSON")
    private List<String> options;

    @Column(name = "help_text", length = 255)
    private String helpText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Boolean active = Boolean.TRUE;
}
