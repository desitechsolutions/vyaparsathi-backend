package com.desitech.vyaparsathi.document.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService service = new QrCodeService();

    @Test
    @DisplayName("upiUri: builds a valid NPCI URI with all parts")
    void upiUriFull() {
        String uri = service.upiUri("acme@hdfc", "Acme Traders",
                new BigDecimal("1197.00"), "INV-42");
        assertNotNull(uri);
        assertTrue(uri.startsWith("upi://pay?"), "Must start with UPI scheme: " + uri);
        assertTrue(uri.contains("pa=acme%40hdfc"), "VPA must be URL-encoded: " + uri);
        assertTrue(uri.contains("pn=Acme+Traders") || uri.contains("pn=Acme%20Traders"),
                "Payee name must be encoded: " + uri);
        assertTrue(uri.contains("am=1197.00"), "Amount must be present: " + uri);
        assertTrue(uri.contains("cu=INR"), "Currency must be INR: " + uri);
        assertTrue(uri.contains("tr=INV-42"), "Reference must be included: " + uri);
    }

    @Test
    @DisplayName("upiUri: omits amount when null or zero")
    void upiUriOmitsZeroAmount() {
        String uri = service.upiUri("shop@bank", "Shop", null, null);
        assertNotNull(uri);
        assertFalse(uri.contains("am="), "Amount must be omitted when null: " + uri);
        assertFalse(uri.contains("tr="), "Ref must be omitted when null: " + uri);
    }

    @Test
    @DisplayName("upiUri: returns null when UPI id is blank")
    void upiUriBlankVpa() {
        assertNull(service.upiUri(null, "x", null, null));
        assertNull(service.upiUri("", "x", null, null));
        assertNull(service.upiUri("   ", "x", null, null));
    }

    @Test
    @DisplayName("encode: returns non-empty PNG bytes for valid content")
    void encodePngShape() {
        byte[] png = service.encode("hello world", 128);
        assertNotNull(png, "Encode should return bytes");
        assertTrue(png.length > 0, "PNG must be non-empty");
        // PNG magic header
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);  // 'P'
        assertEquals((byte) 0x4E, png[2]);  // 'N'
        assertEquals((byte) 0x47, png[3]);  // 'G'
    }

    @Test
    @DisplayName("encode: returns null on blank content instead of throwing")
    void encodeBlankGracefully() {
        assertNull(service.encode(null, 128));
        assertNull(service.encode("", 128));
        assertNull(service.encode("   ", 128));
    }
}
