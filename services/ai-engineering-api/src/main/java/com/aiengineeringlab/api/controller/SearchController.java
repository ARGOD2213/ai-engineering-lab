package com.aiengineeringlab.api.controller;

import com.aiengineeringlab.api.controller.dto.SemanticSearchRequest;
import com.aiengineeringlab.api.domain.SearchResult;
import com.aiengineeringlab.api.service.SemanticSearchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Retrieval pipeline: question → query embedding → pgvector similarity search → top-K documents. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SemanticSearchService searchService;

    public SearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping
    public SearchResult search(@Valid @RequestBody SemanticSearchRequest request) {
        return searchService.search(request.query(), request.topKOrDefault(), request.similarityThresholdOrDefault(),
                request.filterExpression());
    }
}
