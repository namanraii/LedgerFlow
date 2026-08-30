package com.ledgerflow.ingestion.engine;

import com.ledgerflow.ingestion.client.FraudClient;
import com.ledgerflow.ingestion.client.LedgerClient;
import com.ledgerflow.ingestion.dto.FraudEvaluationResponse;
import com.ledgerflow.ingestion.dto.IngestionRequest;
import com.ledgerflow.ingestion.dto.IngestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class IngestionEngineTest {

    private FraudClient fraudClient;
    private LedgerClient ledgerClient;
    private IngestionEngine ingestionEngine;

    @BeforeEach
    void setUp() {
        fraudClient = Mockito.mock(FraudClient.class);
        ledgerClient = Mockito.mock(LedgerClient.class);
        ingestionEngine = new IngestionEngine(fraudClient, ledgerClient, 5);
    }

    @Test
    void testStandardSuccessfulPipeline() throws Exception {
        IngestionRequest req = new IngestionRequest(1L, new BigDecimal("100.00"), "DEBIT");

        when(fraudClient.evaluateRisk(anyLong(), any(), anyString()))
                .thenReturn(Optional.of(new FraudEvaluationResponse(new BigDecimal("0.10"), false, "NORMAL")));

        IngestionResult mockLedgerResult = new IngestionResult();
        mockLedgerResult.setTransactionId(101L);
        mockLedgerResult.setStatus("COMPLETED");
        mockLedgerResult.setRiskScore(new BigDecimal("0.10"));
        mockLedgerResult.setFraudCheckStatus("OK");

        when(ledgerClient.submitTransactionWithRetry(any(), eq(new BigDecimal("0.10")), eq("OK")))
                .thenReturn(mockLedgerResult);

        CompletableFuture<IngestionResult> future = ingestionEngine.processAsync(req);
        IngestionResult result = future.get();

        assertNotNull(result);
        assertEquals(101L, result.getTransactionId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("OK", result.getFraudCheckStatus());
    }

    @Test
    void testFraudServiceDownFailsOpenDegradedMode() throws Exception {
        IngestionRequest req = new IngestionRequest(1L, new BigDecimal("500.00"), "DEBIT");

        // Scenario 1: Fraud service unreachable
        when(fraudClient.evaluateRisk(anyLong(), any(), anyString()))
                .thenReturn(Optional.empty());

        IngestionResult mockLedgerResult = new IngestionResult();
        mockLedgerResult.setTransactionId(102L);
        mockLedgerResult.setStatus("COMPLETED");
        mockLedgerResult.setRiskScore(null);
        mockLedgerResult.setFraudCheckStatus("UNAVAILABLE");

        when(ledgerClient.submitTransactionWithRetry(any(), isNull(), eq("UNAVAILABLE")))
                .thenReturn(mockLedgerResult);

        CompletableFuture<IngestionResult> future = ingestionEngine.processAsync(req);
        IngestionResult result = future.get();

        assertNotNull(result);
        assertEquals(102L, result.getTransactionId());
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("UNAVAILABLE", result.getFraudCheckStatus());
    }
}
