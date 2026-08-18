package com.desitech.vyaparsathi.rbac.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller / service method as requiring one or more permission
 * codes. Enforced by
 * {@link com.desitech.vyaparsathi.rbac.aspect.PermissionCheckAspect} —
 * throws {@link org.springframework.security.access.AccessDeniedException}
 * when the acting user's effective permission set for the active shop
 * does not include the required codes.
 *
 * <p>Usage:
 * <pre>
 *   &#64;RequirePermission("SALES_CREATE")
 *   public SaleDto createSale(...) { ... }
 *
 *   &#64;RequirePermission(value = {"CUSTOMER_VIEW","CUSTOMER_EDIT"}, mode = Mode.ANY)
 *   public CustomerDto quickUpdate(...) { ... }
 * </pre>
 *
 * <p>Both new code and legacy {@code @PreAuthorize("hasRole(...)")}
 * decorations can coexist — the {@link
 * com.desitech.vyaparsathi.rbac.service.PermissionResolver} bridges old
 * role names to the seeded permission sets, so nothing needs to be
 * rewritten in one big-bang.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** One or more permission codes. */
    String[] value();

    /**
     * ALL — user must hold every listed permission (default).
     * ANY — user needs at least one.
     */
    Mode mode() default Mode.ALL;

    enum Mode { ALL, ANY }
}
