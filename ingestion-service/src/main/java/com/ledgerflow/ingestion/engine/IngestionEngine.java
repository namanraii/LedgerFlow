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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class IngestionEngine {
    private static final Logger log = LoggerFactory.getLogger(IngestionEngine.class);

    private final ThreadPoolExecutor executorService;
    private final FraudClient fraudClient;
    private final LedgerClient ledgerClient;

    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);

    public IngestionEngine(FraudClient fraudClient,
                           LedgerClient ledgerClient,
                           @Value("${ingestion.thread.pool.size:25}") int poolSize,
                           @Value("${ingestion.queue.capacity:200}") int queueCapacity) {
        this.fraudClient = fraudClient;
        this.ledgerClient = ledgerClient;
        int workers = Math.max(1, poolSize);
        int capacity = Math.max(workers, queueCapacity);
        AtomicLong counter = new AtomicLong(0);
        this.executorService = new ThreadPoolExecutor(
                workers,
                workers,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                runnable -> {
                    Thread thread = new Thread(runnable, "ingestion-worker-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("Initialized IngestionEngine with {} workers and a bounded queue of {}", workers, capacity);
    }

    public CompletableFuture<IngestionResult> processAsync(IngestionRequest req) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<FraudEvaluationResponse> fraudResp = fraudClient.evaluateRisk(
                    req.getAccountId(), req.getAmount(), req.getTransactionType());

            BigDecimal riskScore = fraudResp.map(FraudEvaluationResponse::getRiskScore).orElse(null);
            String fraudStatus = fraudResp.isPresent() ? "OK" : "UNAVAILABLE";
            String flagReason = fraudResp.map(FraudEvaluationResponse::getReason).orElse(null);

            IngestionResult result = ledgerClient.submitTransactionWithRetry(req, riskScore, fraudStatus, flagReason);

            totalProcessed.incrementAndGet();
            if ("FAILED_RETRYABLE".equals(result.getStatus())) {
                totalErrors.incrementAndGet();
            }

            return result;
        }, executorService);
    }

    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalErrors() { return totalErrors.get(); }

    public Map<String, Object> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("total_processed", totalProcessed.get());
        metrics.put("total_errors", totalErrors.get());
        metrics.put("pool_size", executorService.getPoolSize());
        metrics.put("active_threads", executorService.getActiveCount());
        metrics.put("queue_depth", executorService.getQueue().size());
        metrics.put("queue_capacity", executorService.getQueue().size() + executorService.getQueue().remainingCapacity());
        metrics.put("status", "ACTIVE");
        return metrics;
    }

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
