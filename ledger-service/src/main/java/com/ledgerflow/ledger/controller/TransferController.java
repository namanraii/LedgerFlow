package com.ledgerflow.ledger.controller;

import com.ledgerflow.ledger.dto.TransferRequest;
import com.ledgerflow.ledger.dto.TransferResponse;
import com.ledgerflow.ledger.service.SafeLedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final SafeLedgerService safeLedgerService;

    public TransferController(SafeLedgerService safeLedgerService) {
        this.safeLedgerService = safeLedgerService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        TransferResponse response = safeLedgerService.transfer(request);
        HttpStatus status = response.isAlreadyProcessed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }
}
