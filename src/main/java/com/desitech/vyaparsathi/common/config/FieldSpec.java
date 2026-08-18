package com.desitech.vyaparsathi.common.config;

import java.util.List;

/**
 * Declarative descriptor for a single form field the frontend should
 * render for a given industry. Emitted by
 * {@link IndustryFieldRegistry} and returned to the client by
 * {@link IndustryConfigController}.
 *
 * <p>The frontend's IndustrySlot component iterates a list of these
 * and dispatches on {@link #type} to pick an input widget
 * (text/number/date/select). Adding a new industry attribute is a
 * change here and in the frontend's field-renderer — no schema
 * migration if the attribute lives in the {@code custom_attributes}
 * JSON slot (Phase 4).
 *
 * @param key       Field key. Must exactly match an
 *                  {@code ItemVariantDto} setter, or (for custom
 *                  attributes in Phase 4) the map key inside
 *                  {@code customAttributes}.
 * @param label     Human-readable label the frontend renders next to
 *                  the input.
 * @param type      Widget type — one of "text", "number", "date",
 *                  "select", "boolean".
 * @param required  Whether the frontend should mark the field as
 *                  required. Enforcement lives with Yup on the client
 *                  and bean-validation on the DTO.
 * @param min       Optional min value (only meaningful for "number").
 * @param max       Optional max value (only meaningful for "number").
 * @param options   Optional list of allowed values (only meaningful
 *                  for "select").
 * @param helpText  Optional tooltip / help text shown next to the
 *                  field.
 * @param part      Where the field belongs — "item" (parent) or
 *                  "variant" (per-SKU).
 */
public record FieldSpec(
        String key,
        String label,
        String type,
        boolean required,
        Double min,
        Double max,
        List<String> options,
        String helpText,
        String part
) {
    public static FieldSpec text(String key, String label, boolean required, String part) {
        return new FieldSpec(key, label, "text", required, null, null, null, null, part);
    }

    public static FieldSpec number(String key, String label, boolean required, Double min, Double max, String part) {
        return new FieldSpec(key, label, "number", required, min, max, null, null, part);
    }

    public static FieldSpec select(String key, String label, boolean required, List<String> options, String part) {
        return new FieldSpec(key, label, "select", required, null, null, options, null, part);
    }

    public static FieldSpec date(String key, String label, boolean required, String part) {
        return new FieldSpec(key, label, "date", required, null, null, null, null, part);
    }

    /**
     * Boolean flag field. Named {@code boolFlag} (not {@code boolean}) because
     * "boolean" is a Java reserved word.
     */
    public static FieldSpec boolFlag(String key, String label, boolean required, String part) {
        return new FieldSpec(key, label, "boolean", required, null, null, null, null, part);
    }
}
