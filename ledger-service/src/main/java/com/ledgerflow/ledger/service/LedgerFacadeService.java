package com.ledgerflow.ledger.service;

import com.ledgerflow.ledger.dto.TransactionRequest;
import com.ledgerflow.ledger.dto.TransactionResponse;
import org.springframework.stereotype.Service;

@Service
public class LedgerFacadeService {
    private final SafeLedgerService safeLedgerService;
    private final UnsafeLedgerService unsafeLedgerService;

    public LedgerFacadeService(SafeLedgerService safeLedgerService, UnsafeLedgerService unsafeLedgerService) {
        this.safeLedgerService = safeLedgerService;
        this.unsafeLedgerService = unsafeLedgerService;
    }

    public TransactionResponse processTransaction(TransactionRequest req) {
        if (req.isSafe()) {
            return safeLedgerService.processTransaction(req);
        } else {
            return unsafeLedgerService.processTransactionUnsafe(req);
        }
    }
}
