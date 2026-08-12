package com.desitech.vyaparsathi.gst.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.gst.model.IndianState;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Reference-data endpoints — static lookups any authenticated user needs.
 * Kept separate from {@link GstTaxController} because the filing endpoints
 * require OWNER/ADMIN role, while these lookups drive dropdowns everywhere.
 */
@RestController
@RequestMapping("/api/v1/gst/reference")
public class GstReferenceDataController {

    /** Payload shape for the state dropdown. */
    public record StateOption(String code, String name, boolean unionTerritory) {}

    /**
     * The 37 Indian jurisdictions (28 states + 8 UTs + Other Territory) with
     * their GSTN codes and UT flag. Excludes retired duplicates so the picker
     * only surfaces currently-valid choices. Cached one hour on the client —
     * the list changes rarely (last change: Ladakh in 2019).
     */
    @GetMapping("/states")
    public ResponseEntity<ApiResponse<List<StateOption>>> listStates() {
        List<StateOption> options = IndianState.pickableForDropdown().stream()
            .map(s -> new StateOption(s.getCode(), s.getDisplayName(), s.isUnionTerritory()))
            .collect(Collectors.toList());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(new ApiResponse<>("success", "Indian states and union territories", options));
    }
}
