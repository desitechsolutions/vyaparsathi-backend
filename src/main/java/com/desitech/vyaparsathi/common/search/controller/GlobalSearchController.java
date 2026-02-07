package com.desitech.vyaparsathi.common.search.controller;
import com.desitech.vyaparsathi.common.search.dto.GlobalSearchResponse;
import com.desitech.vyaparsathi.common.search.service.GlobalSearchService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {

    private final GlobalSearchService searchService;

    public GlobalSearchController(GlobalSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<GlobalSearchResponse> search(@RequestParam String q) {
        return searchService.performSearch(q);
    }
}