package com.ledgerflow.ingestion.controller;

import com.ledgerflow.ingestion.dto.IngestionRequest;
import com.ledgerflow.ingestion.dto.IngestionResult;
import com.ledgerflow.ingestion.engine.IngestionEngine;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/ingestion")
public class IngestionController {
    private final IngestionEngine ingestionEngine;

    public IngestionController(IngestionEngine ingestionEngine) {
        this.ingestionEngine = ingestionEngine;
    }

    @PostMapping("/submit")
    public CompletableFuture<ResponseEntity<IngestionResult>> submitTransaction(@Valid @RequestBody IngestionRequest req) {
        return ingestionEngine.processAsync(req).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(ingestionEngine.metrics());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ingestion-service",
                "version", "1.0.0"
        ));
    }
}
