package com.aiengineeringlab.api.controller;

import com.aiengineeringlab.api.controller.dto.EmbeddingRequest;
import com.aiengineeringlab.api.controller.dto.SimilarityRequest;
import com.aiengineeringlab.api.domain.EmbeddingResult;
import com.aiengineeringlab.api.domain.SimilarityReport;
import com.aiengineeringlab.api.service.EmbeddingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the embedding model directly, so you can see what a vector looks like. Nothing is stored. */
@RestController
@RequestMapping("/api/embeddings")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    /** Text in, vector out. */
    @PostMapping
    public EmbeddingResult embed(@Valid @RequestBody EmbeddingRequest request) {
        return embeddingService.embed(request.text());
    }

    /** Embeds 2-10 texts and returns the cosine similarity (and keyword overlap) of every pair. */
    @PostMapping("/similarity")
    public SimilarityReport similarity(@Valid @RequestBody SimilarityRequest request) {
        return embeddingService.compare(request.texts());
    }
}
