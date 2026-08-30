package com.ledgerflow.ingestion.engine;

import com.ledgerflow.ingestion.client.FraudClient;
import com.ledgerflow.ingestion.client.LedgerClient;
import com.ledgerflow.ingestion.dto.FraudEvaluationResponse;
import com.ledgerflow.ingestion.dto.IngestionRequest;
import com.ledgerflow.ingestion.dto.IngestionResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class IngestionEngine {
    private static final Logger log = LoggerFactory.getLogger(IngestionEngine.class);

    private final ExecutorService executorService;
    private final FraudClient fraudClient;
    private final LedgerClient ledgerClient;

    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    public IngestionEngine(FraudClient fraudClient,
                           LedgerClient ledgerClient,
                           @Value("${ingestion.thread.pool.size:25}") int poolSize) {
        this.fraudClient = fraudClient;
        this.ledgerClient = ledgerClient;
        this.executorService = Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            private final AtomicLong counter = new AtomicLong(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ingestion-worker-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        log.info("Initialized IngestionEngine with fixed thread pool of {} workers", poolSize);
    }

    public CompletableFuture<IngestionResult> processAsync(IngestionRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<FraudEvaluationResponse> fraudResp = fraudClient.evaluateRisk(
                    req.getAccountId(), req.getAmount(), req.getTransactionType());

            BigDecimal riskScore = fraudResp.map(FraudEvaluationResponse::getRiskScore).orElse(null);
            String fraudStatus = fraudResp.isPresent() ? "OK" : "UNAVAILABLE";

            IngestionResult result = ledgerClient.submitTransactionWithRetry(req, riskScore, fraudStatus);

            totalProcessed.incrementAndGet();
            if ("FAILED_RETRYABLE".equals(result.getStatus())) {
                totalErrors.incrementAndGet();
            }

            return result;
        }, executorService);
    }

    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalErrors() { return totalErrors.get(); }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down IngestionEngine thread pool");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(3, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
}
