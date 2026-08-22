package com.desitech.vyaparsathi.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateUtilTest {

    @Test
    void testVerifyEmailTemplateWithMustachePlaceholders() {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "TestUser");
        vars.put("verifyLink", "http://localhost:3000/auth/verify-email?token=abc-123-xyz$456");
        vars.put("expiryHours", "24");
        vars.put("currentYear", "2026");

        String html = TemplateUtil.loadTemplate("templates/verify-email.html", vars);

        assertTrue(html.contains("TestUser"), "Should replace {{name}} with TestUser");
        assertTrue(html.contains("http://localhost:3000/auth/verify-email?token=abc-123-xyz$456"), "Should replace {{verifyLink}}");
        assertTrue(html.contains("24 hours"), "Should replace {{expiryHours}}");
        assertTrue(html.contains("2026"), "Should replace {{currentYear}}");
        assertFalse(html.contains("{{"), "Should not contain unparsed mustache placeholders");
        assertFalse(html.contains("}}"), "Should not contain unparsed mustache closing placeholders");
    }

    @Test
    void testResetPasswordTemplateWithDollarBracePlaceholders() {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", "John Doe");
        vars.put("resetLink", "http://localhost:3000/auth/reset-pin?token=token-123");
        vars.put("currentYear", "2026");

        String html = TemplateUtil.loadTemplate("templates/reset-password.html", vars);

        assertTrue(html.contains("John Doe"), "Should replace ${name}");
        assertTrue(html.contains("http://localhost:3000/auth/reset-pin?token=token-123"), "Should replace ${resetLink}");
        assertFalse(html.contains("${name}"), "Should not contain unparsed ${name}");
        assertFalse(html.contains("${resetLink}"), "Should not contain unparsed ${resetLink}");
    }

    @Test
    void testShopInvitationTemplate() {
        Map<String, String> vars = new HashMap<>();
        vars.put("shopName", "My Mart");
        vars.put("inviterName", "Alice");
        vars.put("roleName", "CASHIER");
        vars.put("message", "Welcome!");
        vars.put("acceptLink", "http://localhost:3000/invite/accept?token=tok-789");
        vars.put("expiryHours", "48");
        vars.put("currentYear", "2026");

        String html = TemplateUtil.loadTemplate("templates/shop-invitation.html", vars);

        assertTrue(html.contains("My Mart"));
        assertTrue(html.contains("Alice"));
        assertTrue(html.contains("CASHIER"));
        assertTrue(html.contains("http://localhost:3000/invite/accept?token=tok-789"));
        assertFalse(html.contains("{{acceptLink}}"));
    }
}
