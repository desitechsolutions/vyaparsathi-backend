package com.desitech.vyaparsathi.common.config;

import java.util.List;
import java.util.Map;

/**
 * The complete field spec returned by
 * {@code GET /api/config/industries/{type}/fields}.
 *
 * <p>{@code labels} lets an industry override generic attribute names
 * (e.g. CLOTHING calls attribute1 "Fabric", HARDWARE calls it
 * "Material") without adding a distinct DTO field per industry —
 * the value still lives in the same {@code attribute_1} / {@code
 * attribute_2} column.
 *
 * @param item    Extra fields the frontend should render in the
 *                parent-item form.
 * @param variant Extra fields the frontend should render in the
 *                variant form.
 * @param labels  Optional label overrides for shared fields
 *                (attribute1, attribute2). Key = field key,
 *                value = industry-specific label.
 */
public record IndustryFieldSpec(
        List<FieldSpec> item,
        List<FieldSpec> variant,
        Map<String, String> labels
) {}
