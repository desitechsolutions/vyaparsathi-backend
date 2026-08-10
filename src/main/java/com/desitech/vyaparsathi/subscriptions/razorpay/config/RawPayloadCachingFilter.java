package com.desitech.vyaparsathi.subscriptions.razorpay.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * Servlet filter that wraps the Razorpay webhook request body so it can be
 * read more than once (Spring's {@link HttpServletRequest} input stream is
 * single-read by default).
 *
 * <p>Only activates for paths containing {@code /webhooks/razorpay} to avoid
 * unnecessary overhead on other endpoints.
 */
@Component
public class RawPayloadCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI() != null
                && request.getRequestURI().contains("/webhooks/razorpay")) {
            // Buffer up to 1 MB — well above any realistic Razorpay payload
            ContentCachingRequestWrapper wrappedRequest =
                    new ContentCachingRequestWrapper(request, 1024 * 1024);
            filterChain.doFilter(wrappedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}
