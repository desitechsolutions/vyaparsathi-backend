package com.desitech.vyaparsathi.rbac.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SpEL entry point so controllers can express permission checks in
 * {@code @PreAuthorize} without adding another annotation:
 *
 * <pre>
 *   &#64;PreAuthorize("@perms.has('SALES_CREATE')")
 *   public SaleDto createSale(...) { ... }
 * </pre>
 *
 * <p>The bean is registered under the name {@code perms} (via
 * {@link Component}), matching the {@code @perms} handle used in the
 * SpEL string.
 *
 * <p>Both routes — {@link com.desitech.vyaparsathi.rbac.annotation.RequirePermission}
 * for method-level clarity, and {@code @PreAuthorize("@perms.has(...)")}
 * for classes that already stack Spring Security annotations — go
 * through the same {@link PermissionResolver} so behaviour is identical.
 */
@Component("perms")
@RequiredArgsConstructor
public class PermissionEvaluator {

    private final PermissionResolver resolver;

    public boolean has(String code) {
        return resolver.currentUserHas(code);
    }

    public boolean hasAll(String... codes) {
        return resolver.currentUserHasAll(codes);
    }

    public boolean hasAny(String... codes) {
        return resolver.currentUserHasAny(codes);
    }
}
