package com.desitech.vyaparsathi.common.config;

import com.desitech.vyaparsathi.shop.enums.IndustryType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Read-only endpoints that expose the industry catalogue and the per-
 * industry field spec to the frontend.
 *
 * <p>{@code GET /api/config/industries} lists the enum values so the
 * onboarding UI can render the industry-picker without hard-coding the
 * list on the frontend. {@code GET /api/config/industries/{type}/fields}
 * returns the {@link IndustryFieldSpec} the item / variant form
 * renderer consumes.
 *
 * <p>Neither endpoint carries tenant-scoped data — no shop id is
 * needed to answer them — so they're safe to hit before onboarding
 * completes.
 */
@RestController
@RequestMapping("/api/config/industries")
@RequiredArgsConstructor
public class IndustryConfigController {

    private final IndustryFieldRegistry registry;

    @GetMapping
    public ResponseEntity<List<IndustryType>> listIndustries() {
        return ResponseEntity.ok(Arrays.asList(IndustryType.values()));
    }

    @GetMapping("/{type}/fields")
    public ResponseEntity<IndustryFieldSpec> getFields(@PathVariable String type) {
        // Lenient parsing — an unknown or malformed `type` falls back to
        // GENERAL rather than 400. Keeps the client resilient when a
        // shop's industryType lags behind a backend deploy.
        IndustryType industry = IndustryType.fromString(type);
        return ResponseEntity.ok(registry.forIndustry(industry));
    }
}
