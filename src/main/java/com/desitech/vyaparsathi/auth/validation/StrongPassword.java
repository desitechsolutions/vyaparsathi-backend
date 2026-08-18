package com.desitech.vyaparsathi.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces enterprise-grade password strength:
 *  - Length &ge; 8 (configurable via {@link #minLength()})
 *  - At least one lowercase, one uppercase, one digit
 *  - At least one non-alphanumeric character
 *
 * Applied to registration and change-password DTOs. Frontend runs the same
 * rules client-side (zxcvbn + regex) so users get instant feedback, but the
 * server enforces the contract.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must be at least 8 characters and include an uppercase letter, lowercase letter, digit and special character.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int minLength() default 8;
}
