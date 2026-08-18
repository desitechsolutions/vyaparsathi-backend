package com.desitech.vyaparsathi.auth.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Best-effort User-Agent → "Chrome on macOS" style label parser used
 * to give the Active Sessions UI something human-readable to show
 * per row. Deliberately shallow: enterprise UA parsing (ua-parser-java,
 * UADetector) drags a big lookup table into the runtime; a targeted
 * substring scan gives us 95% coverage for consumer browsers/OSes at
 * near-zero cost.
 *
 * <p>Falls back to the raw UA (truncated) or "Unknown device" when
 * neither browser nor OS matches — never returns null so callers
 * don't have to null-check when persisting.</p>
 */
@Component
public class DeviceLabelParser {

    public String parse(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent;
        String browser = detectBrowser(ua);
        String os = detectOs(ua);
        if (browser != null && os != null) {
            return browser + " on " + os;
        }
        if (browser != null) {
            return browser;
        }
        if (os != null) {
            return os + " device";
        }
        // Keep it short so the UI cell doesn't overflow.
        return ua.length() > 60 ? ua.substring(0, 60) + "…" : ua;
    }

    /**
     * Convenience: parse straight from the request. Also strips the
     * proxy chain out of X-Forwarded-For so the stored IP is the
     * client's, not the load balancer's.
     */
    public String parseUserAgent(HttpServletRequest request) {
        return parse(request == null ? null : request.getHeader("User-Agent"));
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // First hop is the original client. Later hops are proxies.
            int comma = forwarded.indexOf(',');
            String head = (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!head.isEmpty()) {
                return truncate(head, 64);
            }
        }
        String remote = request.getRemoteAddr();
        return truncate(remote, 64);
    }

    public String rawUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        return truncate(ua, 500);
    }

    private String detectBrowser(String ua) {
        // Order matters — Edge advertises Chrome + Safari in its UA,
        // so we probe more specific tokens first.
        if (ua.contains("Edg/")) return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera")) return "Opera";
        if (ua.contains("Brave")) return "Brave";
        if (ua.contains("Firefox/")) return "Firefox";
        if (ua.contains("Chrome/")) return "Chrome";
        if (ua.contains("Safari/")) return "Safari";
        return null;
    }

    private String detectOs(String ua) {
        if (ua.contains("Windows NT 10.0")) return "Windows 10/11";
        if (ua.contains("Windows NT 6.3"))  return "Windows 8.1";
        if (ua.contains("Windows NT 6.1"))  return "Windows 7";
        if (ua.contains("Windows"))         return "Windows";
        if (ua.contains("iPhone"))          return "iPhone";
        if (ua.contains("iPad"))            return "iPad";
        if (ua.contains("Android"))         return "Android";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("CrOS"))            return "ChromeOS";
        if (ua.contains("Linux"))           return "Linux";
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
