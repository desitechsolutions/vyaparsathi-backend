package com.desitech.vyaparsathi.payroll.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter for AES-256-GCM transparent column encryption.
 * Complies with DPDP Act 2023 for PII fields like Aadhaar number.
 *
 * Usage: annotate a field with @Convert(converter = AesEncryptedStringConverter.class)
 *
 * The key must be exactly 32 bytes (256-bit) when Base64-decoded,
 * configured via property: payroll.encryption.key (Base64-encoded 32-byte key)
 * If not configured, encryption is skipped (development mode only).
 */
@Converter
@Component
public class AesEncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptedStringConverter.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${payroll.encryption.key:}")
    private String encryptionKeyBase64;

    /**
     * Encrypt before writing to database.
     * Format: Base64(IV + ciphertext + GCM tag)
     */
    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        SecretKey key = getKey();
        if (key == null) return plaintext; // dev mode: no encryption

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Prepend IV to ciphertext
            byte[] combined = new byte[GCM_IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, GCM_IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed for PII field", e);
            throw new RuntimeException("PII field encryption failed", e);
        }
    }

    /**
     * Decrypt after reading from database.
     */
    @Override
    public String convertToEntityAttribute(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return encrypted;
        SecretKey key = getKey();
        if (key == null) return encrypted; // dev mode: no decryption

        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, "UTF-8");
        } catch (Exception e) {
            log.warn("Decryption failed — possibly plaintext (migrating): {}", e.getMessage());
            return encrypted; // graceful fallback for pre-encryption data migration
        }
    }

    private SecretKey getKey() {
        if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank()) {
            log.warn("payroll.encryption.key not configured — Aadhaar stored in plaintext (development mode only!)");
            return null;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("payroll.encryption.key must decode to exactly 32 bytes (256-bit AES key)");
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            log.error("Failed to load AES encryption key from payroll.encryption.key property", e);
            throw new RuntimeException("AES key configuration error", e);
        }
    }
}
