package com.desitech.vyaparsathi.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A global exception handler to provide consistent error responses across the application.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFoundException(EntityNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
    @ExceptionHandler(UserInactiveException.class)
    public ResponseEntity<Map<String, String>> handleInactiveUser(UserInactiveException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(getErrorMap(ex));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(getErrorMap(ex));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> error = new HashMap<>();
        String errors = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(ObjectError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        error.put("message", errors);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<String> handleInvalidFormat(InvalidFormatException ex) {
        if (ex.getTargetType().isEnum()) {
            Object[] constants = ex.getTargetType().getEnumConstants();
            return ResponseEntity.badRequest()
                    .body("Invalid value '" + ex.getValue() +
                            "'. Allowed values: " + Arrays.toString(constants));
        }
        return ResponseEntity.badRequest().body("Invalid request format.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<Map<String, String>> handleApplicationException(ApplicationException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", "File too large! Please upload a smaller file.");
        return ResponseEntity
                .badRequest()
                .body(error);
    }
    // Add other exception handlers as needed

    private Map<String, String> getErrorMap(Exception ex){
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return error;
    }
    /**
     * DB-level constraint violation. We map anything that looks like a
     * unique-index conflict to 409 Conflict with a user-friendly message,
     * so callers (e.g. onboarding, admin-create-user) can surface an
     * actionable error instead of a raw stack trace. Everything else
     * degrades to 400.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataExceptions(DataIntegrityViolationException ex) {
        String rootMessage = ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();
        String lower = rootMessage != null ? rootMessage.toLowerCase() : "";

        Map<String, String> error = new HashMap<>();

        // Heuristic detection of unique-constraint conflicts. Errors from
        // MySQL, PostgreSQL and H2 all mention "duplicate" or "unique";
        // shop code specifically ships as a "shop_code" or "uk_" index.
        boolean isDuplicate = lower.contains("duplicate") || lower.contains("unique");
        if (isDuplicate) {
            String friendly = "This value is already in use.";
            if (lower.contains("code") && lower.contains("shop")) {
                friendly = "That shop code is already taken — please pick a different one.";
            } else if (lower.contains("email")) {
                friendly = "That email is already registered.";
            } else if (lower.contains("username")) {
                friendly = "That username is already taken.";
            } else if (lower.contains("gstin")) {
                friendly = "That GSTIN is already registered to another shop.";
            }
            error.put("message", friendly);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
        }

        // Not a duplicate — surface a generic bad-request without leaking the SQL.
        error.put("message", "The request could not be completed because it violates a data constraint.");
        return ResponseEntity.badRequest().body(error);
    }
    @ExceptionHandler(SubscriptionException.class)
    public ResponseEntity<Map<String, String>> handleSubscriptionException(SubscriptionException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    @ExceptionHandler(SubscriptionLimitException.class)
    public ResponseEntity<Map<String, String>> handleSubscriptionLimit(SubscriptionLimitException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "LIMIT_EXCEEDED");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(DuplicateItemException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateItemException(DuplicateItemException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(FeatureRestrictedException.class)
    public ResponseEntity<Map<String, Object>> handleFeatureRestricted(FeatureRestrictedException ex) {
        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("code", ex.getCode());
        response.put("feature", ex.getFeature());
        response.put("message", ex.getMessage());

        Map<String, Object> upgradeOptions = new java.util.LinkedHashMap<>();
        upgradeOptions.put("canStartTrial", ex.isCanStartTrial());
        upgradeOptions.put("trialDays", ex.getTrialDays());

        response.put("upgradeOptions", upgradeOptions);
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(response);
    }
}