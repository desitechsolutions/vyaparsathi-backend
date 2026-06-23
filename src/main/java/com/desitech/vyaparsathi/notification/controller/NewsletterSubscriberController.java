package com.desitech.vyaparsathi.notification.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.common.util.TemplateUtil;
import com.desitech.vyaparsathi.notification.dto.NewsletterStatsDto;
import com.desitech.vyaparsathi.notification.dto.NewsletterSubscribeRequest;
import com.desitech.vyaparsathi.notification.dto.NewsletterSubscriberDto;
import com.desitech.vyaparsathi.notification.service.NewsletterSubscriberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Newsletter Subscriber Management")
public class NewsletterSubscriberController {

    private final NewsletterSubscriberService service;

    @PostMapping("/newsletter/subscribe")
    @PreAuthorize("permitAll()")
    @Operation(summary = "Subscribe to VyaparSathi Newsletter")
    public ResponseEntity<ApiResponse<String>> subscribe(@Valid @RequestBody NewsletterSubscribeRequest request) {
        String message = service.subscribe(request);
        return ResponseEntity.ok(new ApiResponse<>("success", message, null));
    }

    @GetMapping(value = "/newsletter/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    @PreAuthorize("permitAll()")
    @Operation(summary = "Unsubscribe from VyaparSathi Newsletter")
    public ResponseEntity<String> unsubscribe(@RequestParam("token") String token) {
        try {
            String email = service.unsubscribe(token);

            Map<String, String> variables = new HashMap<>();
            variables.put("email", email);
            variables.put("currentYear", String.valueOf(LocalDateTime.now().getYear()));

            String html = TemplateUtil.loadTemplate("templates/unsubscribe-confirmation.html", variables);
            return ResponseEntity.ok(html);
        } catch (Exception e) {
            // Friendly error HTML page for user
            String errorHtml = "<!DOCTYPE html>" +
                    "<html><head><title>Unsubscribe Error</title>" +
                    "<style>body{font-family:sans-serif;background-color:#f1f5f9;color:#334155;text-align:center;padding:50px;}" +
                    ".card{background:white;padding:40px;border-radius:12px;display:inline-block;box-shadow:0 4px 6px rgba(0,0,0,0.1);max-width:400px;}" +
                    "h1{color:#ef4444;margin-top:0;}a{color:#1976d2;text-decoration:none;font-weight:bold;}</style></head>" +
                    "<body><div class='card'><h1>Link Invalid or Expired</h1>" +
                    "<p>This unsubscribe link is invalid or has expired. If you believe this is an error, please contact support.</p>" +
                    "<p>Support: <a href='mailto:support@desitechsolutions.com'>support@desitechsolutions.com</a></p>" +
                    "</div></body></html>";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorHtml);
        }
    }

    @GetMapping("/admin/newsletter/subscribers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get newsletter subscribers list (Super Admin only)")
    public ResponseEntity<Page<NewsletterSubscriberDto>> getSubscribers(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "source", required = false) String source,
            @PageableDefault(size = 20, sort = "subscribedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(service.getSubscribers(email, active, source, pageable));
    }

    @GetMapping("/admin/newsletter/stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get newsletter stats dashboard metrics (Super Admin only)")
    public ResponseEntity<NewsletterStatsDto> getStats() {
        return ResponseEntity.ok(service.getStats());
    }

    @GetMapping("/admin/newsletter/export")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Export newsletter subscribers list to CSV (Super Admin only)")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "active", required = false) Boolean active,
            @RequestParam(value = "source", required = false) String source) {

        byte[] csvBytes = service.exportCsv(email, active, source);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=newsletter_subscribers.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }
}
