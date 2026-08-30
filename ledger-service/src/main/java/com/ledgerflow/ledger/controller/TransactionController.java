package com.ledgerflow.ledger.controller;

import com.ledgerflow.ledger.domain.Transaction;
import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.dto.TransactionResponse;
import com.ledgerflow.ledger.repository.TransactionRepository;
import com.ledgerflow.ledger.service.LedgerFacadeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final LedgerFacadeService ledgerFacadeService;
    private final TransactionRepository transactionRepository;

    public TransactionController(LedgerFacadeService ledgerFacadeService, TransactionRepository transactionRepository) {
        this.ledgerFacadeService = ledgerFacadeService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest req) {
        TransactionResponse resp = ledgerFacadeService.processTransaction(req);
        // If already processed (idempotency hit), return HTTP 200; else HTTP 201 Created
        HttpStatus status = resp.isAlreadyProcessed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resp);
    }

    @GetMapping
    public ResponseEntity<Page<Transaction>> getRecentTransactions(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(transactionRepository.findByOrderByCreatedAtDesc(PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getTransaction(@PathVariable("id") Long id) {
        return transactionRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
