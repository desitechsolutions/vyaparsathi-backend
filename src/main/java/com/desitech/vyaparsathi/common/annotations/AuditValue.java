package com.desitech.vyaparsathi.common.annotations;

import java.lang.annotation.*;

/**
 * Marker to tell the AuditAspect which field is the primary identifier (e.g., Invoice No, Name).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditValue {
}