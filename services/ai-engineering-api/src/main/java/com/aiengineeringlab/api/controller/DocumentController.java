package com.aiengineeringlab.api.controller;

import com.aiengineeringlab.api.controller.dto.BatchCreateDocumentsRequest;
import com.aiengineeringlab.api.controller.dto.CreateDocumentRequest;
import com.aiengineeringlab.api.domain.BatchIngestionResult;
import com.aiengineeringlab.api.domain.NewDocument;
import com.aiengineeringlab.api.domain.StoredDocument;
import com.aiengineeringlab.api.service.DocumentIngestionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Ingestion pipeline: text + metadata → embedding → pgvector. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public ResponseEntity<StoredDocument> create(@Valid @RequestBody CreateDocumentRequest request) {
        StoredDocument stored = ingestionService.ingest(toNewDocument(request));
        return ResponseEntity.created(URI.create("/api/documents/" + stored.id())).body(stored);
    }

    /** Loads many documents at once, e.g. the sample dataset in 02-vector-databases/dataset. */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchIngestionResult createBatch(@Valid @RequestBody BatchCreateDocumentsRequest request) {
        return ingestionService.ingestAll(request.documents().stream().map(DocumentController::toNewDocument).toList());
    }

    /** Reads a stored document back. {@code includeEmbedding=true} also returns the vector stored in pgvector. */
    @GetMapping("/{id}")
    public StoredDocument get(@PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeEmbedding) {
        return ingestionService.findById(id, includeEmbedding);
    }

    private static NewDocument toNewDocument(CreateDocumentRequest request) {
        return new NewDocument(request.id(), request.text(), request.metadata() != null ? request.metadata() : Map.of());
    }
}
