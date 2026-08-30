package com.ledgerflow.ingestion.controller;

import com.ledgerflow.ingestion.dto.IngestionRequest;
import com.ledgerflow.ingestion.dto.IngestionResult;
import com.ledgerflow.ingestion.engine.IngestionEngine;
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
    public CompletableFuture<ResponseEntity<IngestionResult>> submitTransaction(@RequestBody IngestionRequest req) {
        return ingestionEngine.processAsync(req).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(Map.of(
                "total_processed", ingestionEngine.getTotalProcessed(),
                "total_errors", ingestionEngine.getTotalErrors(),
                "status", "ACTIVE"
        ));
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
