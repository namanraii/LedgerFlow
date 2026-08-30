package com.ledgerflow.ledger.controller;

import com.ledgerflow.ledger.domain.FraudFlag;
import com.ledgerflow.ledger.dto.FraudFlagRequest;
import com.ledgerflow.ledger.dto.ReviewFlagRequest;
import com.ledgerflow.ledger.repository.FraudFlagRepository;
import com.ledgerflow.ledger.service.FraudFlagService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FraudFlagController {
    private final FraudFlagService fraudFlagService;

    public FraudFlagController(FraudFlagService fraudFlagService) {
        this.fraudFlagService = fraudFlagService;
    }

    @PostMapping("/fraud-flags")
    public ResponseEntity<FraudFlag> createFraudFlag(@Valid @RequestBody FraudFlagRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fraudFlagService.createFraudFlag(req));
    }

    @GetMapping("/fraud-flags/unreviewed")
    public ResponseEntity<List<FraudFlag>> getUnreviewedFlags() {
        return ResponseEntity.ok(fraudFlagService.getUnreviewedFlags());
    }

    // Section 10 Reporting Query endpoint
    @GetMapping("/reports/flagged")
    public ResponseEntity<List<FraudFlagRepository.FlaggedReportProjection>> getFlaggedReport() {
        return ResponseEntity.ok(fraudFlagService.getFlaggedReport());
    }

    @PostMapping("/fraud-flags/{id}/review")
    public ResponseEntity<FraudFlag> reviewFlag(@PathVariable("id") Long id, @Valid @RequestBody ReviewFlagRequest req) {
        return ResponseEntity.ok(fraudFlagService.reviewFlag(id, req));
    }
}
