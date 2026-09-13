package com.desitech.vyaparsathi.compliance.controller;

import com.desitech.vyaparsathi.common.payload.ApiResponse;
import com.desitech.vyaparsathi.gst.dto.HsnMasterDto;
import com.desitech.vyaparsathi.gst.service.HsnMasterService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/compliance/hsn")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class HsnMasterController {

    private final HsnMasterService hsnService;

    public HsnMasterController(HsnMasterService hsnService) {
        this.hsnService = hsnService;
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<HsnMasterDto>>> search(@RequestParam String q) {
        List<HsnMasterDto> results = hsnService.search(q);
        return ResponseEntity.ok(new ApiResponse<>("success", "HSN search complete", results));
    }
}
